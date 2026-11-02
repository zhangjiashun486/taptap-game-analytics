package org.example.base_server.service.impl;

import org.example.base_server.dao.GameDao;
import org.example.base_server.dao.QaDao;
import org.example.base_server.dao.RecommendDao;
import org.example.base_server.service.DeepSeekService;
import org.example.base_server.service.QaAgentService;
import org.example.base_server.service.QaService;
import org.example.base_server.vo.ClusterInfo;
import org.example.base_server.vo.QaAnswer;
import org.example.base_server.vo.RecommendItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据问答：优先使用规则查询 MySQL；可选调用 DeepSeek 对结果做自然语言增强。
 *   1. 用关键词/正则识别意图（评分条件、TopN、标签、相似游戏、数量统计）；
 *   2. 转成 SQL 查询；
 *   3. 把结果组织成自然语言答案。
 */
@Service
public class QaServiceImpl implements QaService {

    /** 指标关键词 → 数据库列（白名单，防 SQL 注入） */
    private static final Map<String, String> METRIC_MAP = Map.of(
            "预约", "reserve_count",
            "关注", "fans_count",
            "粉丝", "fans_count",
            "评论", "review_count",
            "热度", "hits_total"
    );

    private static final Pattern RATING_PATTERN = Pattern.compile("评分[^0-9]{0,5}(\\d+(?:\\.\\d+)?)");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)\\s*[款个条]");
    /** 复杂分析问法交给只读 Text-to-SQL，而不是误套简单 TopN/标签规则。 */
    private static final Pattern COMPLEX_QUERY_PATTERN = Pattern.compile(
            "(平均|占比|比例|分布|趋势|分别|每家|各厂商|各个厂商|按.+统计|分组|每年|哪一年|哪些年|同比|环比)"
    );

    private final QaDao qaDao;
    private final GameDao gameDao;
    private final RecommendDao recommendDao;
    private final DeepSeekService deepSeekService;
    private final QaAgentService qaAgentService;

    public QaServiceImpl(QaDao qaDao, GameDao gameDao, RecommendDao recommendDao,
                           DeepSeekService deepSeekService,
                           QaAgentService qaAgentService) {
        this.qaDao = qaDao;
        this.gameDao = gameDao;
        this.recommendDao = recommendDao;
        this.deepSeekService = deepSeekService;
        this.qaAgentService = qaAgentService;
    }

    @Override
    public QaAnswer ask(Long userId, String question) {
        String q = question == null ? "" : question.trim().replace(" ", "");
        QaAnswer answer = new QaAnswer();
        answer.setItems(List.of());
        answer.setTotal(0);

        if (q.isEmpty()) {
            answer.setIntent("FALLBACK");
            answer.setAnswer("请输入一个问题，例如：评分 9 分以上的游戏有哪些？");
        } else if (q.contains("类似") || q.contains("相似") || q.contains("差不多")) {
            answerSimilar(userId, q, answer);
        } else if ((q.contains("多少") || q.contains("几款")) &&
                   (q.contains("采集") || q.contains("一共") || q.contains("总共") || q.contains("平台"))) {
            long total = qaDao.countGames();
            answer.setIntent("COUNT");
            answer.setTotal((int) total);
            answer.setAnswer("当前数据库共收录 " + total + " 款游戏（TapTap 公开数据，含 607 个标签）。");
        } else if (isComplexDataQuestion(q)) {
            answer.setIntent("FALLBACK");
            answer.setAnswer("这个问题需要进一步查询数据库。");
        } else if (q.contains("评分") && (q.contains("以上") || q.contains("之上") || q.contains("不低于"))) {
            answerRating(q, answer);
        } else if (q.contains("最高") || q.contains("最多") || q.toLowerCase().contains("top") || q.contains("前")) {
            answerTopN(q, answer);
        } else {
            String tag = matchTag(q);
            if (tag != null) {
                List<RecommendItem> items = qaDao.findByTag(tag, 10);
                answer.setIntent("TAG");
                answer.setTotal(items.size());
                answer.setItems(items);
                answer.setAnswer("包含标签「" + tag + "」的游戏（按关注数排序，展示前 " + items.size() + " 款）：");
            } else {
                answer.setIntent("FALLBACK");
                answer.setAnswer("暂时无法理解这个问题，可以试试：评分 9 分以上的游戏 / 预约最高的 5 款游戏 / 类似原神的游戏 / 一共采集了多少款游戏。");
            }
        }
        // 规则问答默认来自数据库
        answer.setSource("DB");
        answer.setVerified(true);

        if ("FALLBACK".equals(answer.getIntent()) && question != null && !question.isBlank()) {
            QaAnswer enhanced = qaAgentService.answer(userId, question);
            if (enhanced != null) {
                answer = enhanced;
            } else {
                answer.setSource("SYSTEM");
                answer.setVerified(false);
                answer.setWarning("当前问题没有匹配到数据库查询或知识补充。");
                answer.setMode("RULE");
            }
        } else if (deepSeekService.isEnabled()) {
            String enhanced = deepSeekService.answerGameQuestion(question, buildDataContext(answer));
            if (enhanced != null && !enhanced.isBlank()) {
                answer.setAnswer(enhanced);
                answer.setMode("LLM");
            } else {
                answer.setMode("RULE");
            }
        } else {
            answer.setMode("RULE");
        }

        qaDao.insertLog(userId, question, answer.getIntent(), answer.getAnswer(), answer.getMode(), answer.getTotal());
        return answer;
    }

    /** 评分阈值查询：如“评分 9 分以上的游戏” */
    private void answerRating(String q, QaAnswer answer) {
        Matcher m = RATING_PATTERN.matcher(q);
        BigDecimal min = m.find() ? new BigDecimal(m.group(1)) : new BigDecimal("9");
        List<RecommendItem> items = qaDao.findByRatingAtLeast(min, 10);
        answer.setIntent("RATING");
        answer.setTotal(items.size());
        answer.setItems(items);
        answer.setAnswer("评分不低于 " + min + " 分的游戏（按评分排序，展示前 " + items.size() + " 款）：");
    }

    /** TopN 查询：如“预约最高的 5 款游戏”、“关注最多的 10 款” */
    private void answerTopN(String q, QaAnswer answer) {
        String metric = null;
        for (Map.Entry<String, String> e : METRIC_MAP.entrySet()) {
            if (q.contains(e.getKey())) { metric = e.getValue(); break; }
        }
        if (metric == null) metric = "rating_score";     // 没说指标默认按评分

        int limit = 5;
        Matcher m = NUMBER_PATTERN.matcher(q);
        if (m.find()) limit = Math.min(Integer.parseInt(m.group(1)), 50);

        List<RecommendItem> items = qaDao.findTopBy(metric, limit);
        answer.setIntent("TOPN");
        answer.setTotal(items.size());
        answer.setItems(items);
        answer.setAnswer("按「" + metricName(metric) + "」排序的前 " + items.size() + " 款游戏：");
    }

    /** 相似游戏：如“类似原神的游戏” */
    private void answerSimilar(Long userId, String q, QaAnswer answer) {
        String keyword = q.replace("类似", "").replace("相似", "")
                .replace("有没有", "").replace("差不多", "")
                .replace("的游戏", "").replace("游戏", "")
                .replace("推荐", "").replace("一下", "").replace("？", "").replace("?", "");
        RecommendItem source = qaDao.findGameByTitle(keyword);
        if (source == null) {
            answer.setIntent("SIMILAR");
            answer.setAnswer("没有找到叫《" + keyword + "》的游戏，换个名称试试？");
            return;
        }

        List<RecommendItem> items = List.of();
        Long runId = recommendDao.findLatestRunId();
        if (runId != null) {
            ClusterInfo cluster = recommendDao.findCluster(runId, source.getGameId());
            if (cluster != null) {
                items = recommendDao.findSameClusterGames(runId, cluster.getClusterId(), userId, 10);
                answer.setAnswer("与《" + source.getTitle() + "》相似的游戏（同属「" + cluster.getClusterName() + "」，展示 " + items.size() + " 款）：");
            }
        }
        if (items.isEmpty()) {
            List<String> tags = gameDao.findTags(source.getGameId());
            if (!tags.isEmpty()) {
                items = recommendDao.findGamesByTag(tags.get(0), userId, 10);
                answer.setAnswer("与《" + source.getTitle() + "》相似的游戏（同标签「" + tags.get(0) + "」，展示 " + items.size() + " 款）：");
            }
        }
        answer.setIntent("SIMILAR");
        answer.setTotal(items.size());
        answer.setItems(items);
    }

    /** 包含聚合、分组、比例等要求时，优先交给只读 SQL 代理。 */
    private boolean isComplexDataQuestion(String q) {
        return COMPLEX_QUERY_PATTERN.matcher(q).find();
    }

    /** 在问题里找标签（取匹配到的最长标签，避免"游戏"这种短词误判） */
    private String matchTag(String q) {
        String best = null;
        for (String tag : qaDao.findAllTagNames()) {
            if (tag != null && tag.length() >= 2 && q.contains(tag)) {
                if (best == null || tag.length() > best.length()) best = tag;
            }
        }
        return best;
    }

    private String metricName(String column) {
        return switch (column) {
            case "reserve_count" -> "预约数";
            case "fans_count" -> "关注数";
            case "review_count" -> "评论数";
            case "hits_total" -> "热度";
            default -> "评分";
        };
    }

    /** 把数据库查到的结构化结果压缩成提示词上下文，避免把无关数据交给大模型。 */
    private String buildDataContext(QaAnswer answer) {
        StringBuilder sb = new StringBuilder();
        sb.append("意图: ").append(answer.getIntent()).append('\n');
        sb.append("命中数量: ").append(answer.getTotal()).append('\n');
        sb.append("规则版回答: ").append(answer.getAnswer()).append('\n');

        if (answer.getItems() != null && !answer.getItems().isEmpty()) {
            sb.append("相关游戏:\n");
            int limit = Math.min(answer.getItems().size(), 10);
            for (int i = 0; i < limit; i++) {
                RecommendItem item = answer.getItems().get(i);
                sb.append(i + 1).append(". ")
                        .append(item.getTitle())
                        .append("；评分=").append(item.getRatingScore())
                        .append("；关注=").append(item.getFansCount())
                        .append("；厂商=").append(item.getDeveloper())
                        .append('\n');
            }
        }
        return sb.toString();
    }
}
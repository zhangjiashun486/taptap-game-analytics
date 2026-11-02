package org.example.base_server.service.impl;

import org.example.base_server.service.DeepSeekService;
import org.example.base_server.service.QaAgentService;
import org.example.base_server.service.ReadOnlySqlService;
import org.example.base_server.vo.QaAnswer;
import org.example.base_server.vo.SqlPlan;
import org.example.base_server.vo.SqlQueryResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 规则无法识别时的增强问数：DeepSeek 生成只读 SQL，或提供未验证的通用补充。 */
@Service
public class QaAgentServiceImpl implements QaAgentService {

    private static final String SCHEMA = """
            数据库：MySQL 5.6，库名 taptap_course。
            可用表：
            t_game(game_id, title, rating_score, latest_score, review_count, fans_count,
                   hits_total, wish_count, reserve_count, price, platform, released_time,
                   developer, intro)
            t_tag(tag_id, tag_name)
            t_game_tag(game_id, tag_id)
            t_cluster_run(run_id, algorithm, k_value, silhouette, sse, created_at)
            t_cluster_result(run_id, game_id, cluster_id)
            t_cluster_meta(run_id, cluster_id, cluster_name, size, keywords)
            t_behavior(id, game_id, behavior_type, score, created_at)
            t_behavior.behavior_type 可为 VIEW / FAVORITE / LIKE / PLAYED / RATE。
            禁止查询用户、密码、登录日志等表。
            """;

    private static final String SQL_PLAN_PROMPT = """
            你是 MySQL 5.6 只读查询生成器。
            你只能根据给定的数据库表和字段生成 SELECT 查询。
            规则：
            1. 只能查询白名单表，不能查询 t_user、t_login_log；
            2. 只能生成一条 SELECT，不能包含分号、注释、UNION、SELECT *；
            3. LIMIT 不得超过 50；
            4. 如果问题所需信息不在表结构中，必须返回 CAN_ANSWER=false；
            5. 多表 JOIN 是允许的，但只能连接白名单表；
            6. 用户说“厂商/开发商”时对应 developer；说“发行年份/时间”时对应 released_time；
               用户说“标签”时，需要通过 t_game_tag 关联 t_tag.tag_name；
            7. 分组统计要使用 GROUP BY；排名使用 ORDER BY ... DESC；聚合后可用 HAVING；
            8. 只要表结构和字段能够支持问题，就返回 CAN_ANSWER=true，不要因为需要 JOIN 或聚合就直接拒绝；
            9. 只输出以下三行，不要 Markdown，不要解释：
            CAN_ANSWER=true 或 false
            SQL=单行 SQL；无法回答时留空
            REASON=简短原因

            示例（只示范 JOIN + GROUP BY 的写法，不要把示例条件照搬到其他问题）：
            SELECT g.developer, COUNT(*) AS game_count, AVG(g.rating_score) AS avg_rating
            FROM t_game g
            JOIN t_game_tag gt ON gt.game_id = g.game_id
            JOIN t_tag t ON t.tag_id = gt.tag_id
            WHERE t.tag_name = '示例标签' AND YEAR(g.released_time) >= 2021 AND g.rating_score IS NOT NULL
            GROUP BY g.developer
            HAVING COUNT(*) >= 2
            ORDER BY avg_rating DESC LIMIT 10
            """;

    private static final String SQL_ANSWER_PROMPT = """
            你是 TapTap 数据分析助手。
            只能依据用户问题、SQL 和查询结果回答，不得补充查询结果中没有的数据。
            不得编造数字、游戏、厂商或时间。
            请用简洁自然的中文回答，控制在 180 字以内。
            """;

    private static final String GENERAL_PROMPT = """
            你是通用知识助手。当前数据库没有能回答该问题的数据。
            可以给出一般性知识补充，但必须明确这不是数据库验证结果。
            涉及实时数据、精确数字或最新信息时，不要编造，直接说明无法确认。
            请用简洁中文回答，控制在 160 字以内。
            """;

    private static final Pattern CAN_ANSWER_PATTERN =
            Pattern.compile("(?i)CAN_ANSWER\\s*[=:]\\s*(true|false)");
    private static final Pattern SQL_PATTERN =
            Pattern.compile("(?is)SQL\\s*[=:]\\s*(.+?)(?=\\s+REASON\\s*[=:]|$)");
    private static final Pattern REASON_PATTERN =
            Pattern.compile("(?is)REASON\\s*[=:]\\s*(.+)$");

    private final DeepSeekService deepSeekService;
    private final ReadOnlySqlService readOnlySqlService;

    public QaAgentServiceImpl(DeepSeekService deepSeekService,
                              ReadOnlySqlService readOnlySqlService) {
        this.deepSeekService = deepSeekService;
        this.readOnlySqlService = readOnlySqlService;
    }

    @Override
    public QaAnswer answer(Long userId, String question) {
        if (!deepSeekService.isEnabled()) {
            return null;
        }
        if (!readOnlySqlService.isAvailable()) {
            return generalAnswer(question, "数据库问数功能未配置");
        }

        String planText = deepSeekService.chat(
                SQL_PLAN_PROMPT,
                "用户问题：\n" + question + "\n\n数据库结构：\n" + SCHEMA
        );
        SqlPlan plan = parsePlan(planText);
        if (plan == null) {
            return generalAnswer(question, "未能生成安全查询");
        }
        if (!plan.canAnswer() || plan.sql() == null || plan.sql().isBlank()) {
            return generalAnswer(question, plan.reason());
        }

        SqlQueryResult result = readOnlySqlService.execute(plan.sql());
        if (!result.success()) {
            return generalAnswer(question, "数据库查询未成功：" + result.error());
        }
        if (result.rowCount() == 0) {
            return dbAnswer("数据库中没有找到符合条件的数据。", plan.sql(), 0, "SQL");
        }

        String summary = deepSeekService.chat(
                SQL_ANSWER_PROMPT,
                "用户问题：\n" + question +
                        "\n\n执行的 SQL：\n" + result.sql() +
                        "\n\n查询结果：\n" + result.text()
        );
        if (summary == null || summary.isBlank()) {
            String fallback = "查询到 " + result.rowCount() + " 条结果：\n" + result.text();
            return dbAnswer(limitText(fallback, 1600), result.sql(), result.rowCount(), "SQL");
        }
        return dbAnswer(summary, result.sql(), result.rowCount(), "SQL_LLM");
    }
    private SqlPlan parsePlan(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.replace("```", "").trim();
        Matcher canMatcher = CAN_ANSWER_PATTERN.matcher(normalized);
        boolean hasMarker = normalized.toUpperCase(Locale.ROOT).contains("CAN_ANSWER");
        boolean canAnswer = canMatcher.find() && "true".equalsIgnoreCase(canMatcher.group(1));
        if (!hasMarker) {
            return new SqlPlan(false, null, "模型未按约定格式返回");
        }

        Matcher sqlMatcher = SQL_PATTERN.matcher(normalized);
        String sql = sqlMatcher.find() ? sqlMatcher.group(1).trim() : "";
        sql = sql.replaceAll("[\\r\\n]+", " ").trim();
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }

        Matcher reasonMatcher = REASON_PATTERN.matcher(normalized);
        String reason = reasonMatcher.find() ? reasonMatcher.group(1).trim() : "";
        if (!canAnswer) {
            sql = "";
        }
        return new SqlPlan(canAnswer, sql, reason);
    }

    private QaAnswer generalAnswer(String question, String reason) {
        String detail = reason == null || reason.isBlank() ? "数据库没有对应数据" : reason;
        String general = deepSeekService.chat(
                GENERAL_PROMPT,
                "用户问题：\n" + question + "\n\n数据库无法回答的原因：\n" + detail
        );
        if (general == null || general.isBlank()) {
            general = "暂时无法从现有数据库或通用知识中给出可靠回答。";
        }
        String answer = "数据库中没有收录能够回答该问题的数据。\n\n"
                + general
                + "\n\n说明：以上为通用知识补充，未经数据库验证，可能与最新情况不一致。";
        QaAnswer result = new QaAnswer();
        result.setIntent("GENERAL");
        result.setAnswer(answer);
        result.setTotal(0);
        result.setItems(List.of());
        result.setMode("LLM_GENERAL");
        result.setSource("GENERAL");
        result.setVerified(false);
        result.setWarning("通用知识补充，未经数据库验证，可能过时或不准确。");
        return result;
    }

    private QaAnswer dbAnswer(String answer, String sql, int total, String mode) {
        QaAnswer result = new QaAnswer();
        result.setIntent("SQL_QUERY");
        result.setAnswer(answer);
        result.setTotal(total);
        result.setItems(List.of());
        result.setMode(mode);
        result.setSource("DB");
        result.setVerified(true);
        result.setSql(sql);
        return result;
    }

    private String limitText(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n……";
    }
}
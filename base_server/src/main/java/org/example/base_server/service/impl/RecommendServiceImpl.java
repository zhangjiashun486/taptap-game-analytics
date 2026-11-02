package org.example.base_server.service.impl;

import org.example.base_server.dao.GameDao;
import org.example.base_server.dao.RecommendDao;
import org.example.base_server.service.RecommendService;
import org.example.base_server.vo.BehaviorItem;
import org.example.base_server.vo.ClusterInfo;
import org.example.base_server.vo.RecommendItem;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RecommendServiceImpl implements RecommendService {

    private final RecommendDao recommendDao;
    private final GameDao gameDao;

    public RecommendServiceImpl(RecommendDao recommendDao, GameDao gameDao) {
        this.recommendDao = recommendDao;
        this.gameDao = gameDao;
    }

    @Override
    public Map<String, Object> recommend(Long userId, int limit) {
        // 参数保护
        if (limit < 1) limit = 10;
        if (limit > 50) limit = 50;

        Long runId = recommendDao.findLatestRunId();
        // 1) 读取用户最近的"正向行为"（收藏/点赞/玩过/评分）——就是查 t_behavior 表
        List<BehaviorItem> positives = recommendDao.findPositiveBehaviors(userId, 5);

        // 2) 没有行为（或没有聚类结果）→ 冷启动：热门高分推荐
        if (positives.isEmpty() || runId == null) {
            List<RecommendItem> list = recommendDao.findTopGames(userId, limit);
            list.forEach(item -> item.setReason("高分热门推荐"));
            return wrap(list, "COLD_START");
        }

        // 3) 有行为 → 以最近 5 条行为为"种子"，找同簇 + 同标签的游戏
        Map<Long, RecommendItem> candidates = new LinkedHashMap<>();   // gameId -> 推荐条目
        Map<Long, Integer> scores = new HashMap<>();                  // gameId -> 候选分数
        Map<Long, String> reasons = new HashMap<>();                  // gameId -> 推荐理由

        for (BehaviorItem behavior : positives) {
            String action = actionText(behavior.getBehaviorType());
            String seedReason = "因为你" + action + "了《" + behavior.getGameTitle() + "》";

            // 3.1 同簇推荐（权重 2）
            ClusterInfo cluster = recommendDao.findCluster(runId, behavior.getGameId());
            if (cluster != null) {
                List<RecommendItem> sameCluster = recommendDao.findSameClusterGames(
                        runId, cluster.getClusterId(), userId, 10);
                for (RecommendItem item : sameCluster) {
                    candidates.putIfAbsent(item.getGameId(), item);
                    scores.merge(item.getGameId(), 2, Integer::sum);
                    reasons.putIfAbsent(item.getGameId(),
                            seedReason + "，同属「" + cluster.getClusterName() + "」");
                    item.setClusterName(cluster.getClusterName());
                }
            }

            // 3.2 同标签推荐（每个游戏取前 2 个标签，权重 1）
            List<String> tags = gameDao.findTags(behavior.getGameId());
            for (String tag : tags.stream().limit(2).toList()) {
                List<RecommendItem> sameTag = recommendDao.findGamesByTag(tag, userId, 8);
                for (RecommendItem item : sameTag) {
                    candidates.putIfAbsent(item.getGameId(), item);
                    scores.merge(item.getGameId(), 1, Integer::sum);
                    reasons.putIfAbsent(item.getGameId(),
                            seedReason + "，同标签「" + tag + "」");
                }
            }
        }

        // 4) 按“分数 → 关注数”排序，取前 limit 条
        List<RecommendItem> result = candidates.values().stream()
                .sorted(Comparator.comparingInt((RecommendItem i) -> scores.getOrDefault(i.getGameId(), 0))
                        .reversed()
                        .thenComparing(RecommendItem::getFansCount,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();

        result.forEach(item -> item.setReason(reasons.get(item.getGameId())));
        return wrap(result, "PERSONALIZED");
    }

    /** 把行为类型翻译成中文（用于推荐理由） */
    private String actionText(String behaviorType) {
        return switch (behaviorType) {
            case "FAVORITE" -> "收藏";
            case "LIKE" -> "点赞";
            case "PLAYED" -> "玩过";
            case "RATE" -> "评分";
            default -> "浏览";
        };
    }

    /** 组装统一的分页/列表结构 */
    private Map<String, Object> wrap(List<RecommendItem> list, String mode) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        data.put("mode", mode);   // PERSONALIZED=个性化推荐；COLD_START=冷启动
        return data;
    }
}

package org.example.base_server.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.base_server.vo.BehaviorItem;
import org.example.base_server.vo.ClusterInfo;
import org.example.base_server.vo.RecommendItem;

import java.util.List;

/**
 * 推荐相关的数据查询。
 *
 * 读取用户行为的核心 SQL：
 *   SELECT ... FROM t_behavior b JOIN t_game g ON b.game_id = g.game_id
 *   WHERE b.user_id = #{userId} AND b.behavior_type IN ('FAVORITE','LIKE','PLAYED','RATE')
 *   ORDER BY b.id DESC LIMIT #{limit}
 */
@Mapper
public interface RecommendDao {

    /** 最近一次聚类运行 ID */
    @Select("SELECT MAX(run_id) FROM t_cluster_run")
    Long findLatestRunId();

    /** 读取用户"正向行为"记录（收藏/点赞/玩过/评分），按时间倒序取最近 N 条 */
    @Select("SELECT b.game_id AS gameId, g.title AS gameTitle, b.behavior_type AS behaviorType, " +
            "       b.score, b.created_at AS createdAt " +
            "FROM t_behavior b JOIN t_game g ON b.game_id = g.game_id " +
            "WHERE b.user_id = #{userId} " +
            "  AND b.behavior_type IN ('FAVORITE','LIKE','PLAYED','RATE') " +
            "ORDER BY b.id DESC LIMIT #{limit}")
    List<BehaviorItem> findPositiveBehaviors(@Param("userId") Long userId, @Param("limit") int limit);

    /** 查询某款游戏所属的簇（最新聚类批次） */
    @Select("SELECT r.cluster_id AS clusterId, m.cluster_name AS clusterName " +
            "FROM t_cluster_result r " +
            "JOIN t_cluster_meta m ON m.run_id = r.run_id AND m.cluster_id = r.cluster_id " +
            "WHERE r.run_id = #{runId} AND r.game_id = #{gameId}")
    ClusterInfo findCluster(@Param("runId") Long runId, @Param("gameId") Long gameId);

    /** 同簇推荐：找同一簇、且用户没交互过的游戏，按关注数排序 */
    @Select("SELECT g.game_id AS gameId, g.title, g.rating_score AS ratingScore, " +
            "       g.fans_count AS fansCount, g.icon_url AS iconUrl, g.developer " +
            "FROM t_cluster_result r JOIN t_game g ON g.game_id = r.game_id " +
            "WHERE r.run_id = #{runId} AND r.cluster_id = #{clusterId} " +
            "  AND g.game_id NOT IN (SELECT game_id FROM t_behavior WHERE user_id = #{userId}) " +
            "ORDER BY g.fans_count DESC LIMIT #{limit}")
    List<RecommendItem> findSameClusterGames(@Param("runId") Long runId,
                                             @Param("clusterId") Integer clusterId,
                                             @Param("userId") Long userId,
                                             @Param("limit") int limit);

    /** 同标签推荐：找带同一标签、且用户没交互过的游戏 */
    @Select("SELECT g.game_id AS gameId, g.title, g.rating_score AS ratingScore, " +
            "       g.fans_count AS fansCount, g.icon_url AS iconUrl, g.developer " +
            "FROM t_game_tag gt " +
            "JOIN t_tag t ON gt.tag_id = t.tag_id " +
            "JOIN t_game g ON gt.game_id = g.game_id " +
            "WHERE t.tag_name = #{tagName} " +
            "  AND g.game_id NOT IN (SELECT game_id FROM t_behavior WHERE user_id = #{userId}) " +
            "ORDER BY g.fans_count DESC LIMIT #{limit}")
    List<RecommendItem> findGamesByTag(@Param("tagName") String tagName,
                                       @Param("userId") Long userId,
                                       @Param("limit") int limit);

    /** 冷启动推荐：没有行为的用户，按评分 + 关注数推荐热门高分游戏 */
    @Select("SELECT g.game_id AS gameId, g.title, g.rating_score AS ratingScore, " +
            "       g.fans_count AS fansCount, g.icon_url AS iconUrl, g.developer " +
            "FROM t_game g " +
            "WHERE g.rating_score IS NOT NULL " +
            "  AND g.review_count >= 1000 " +
            "  AND g.game_id NOT IN (SELECT game_id FROM t_behavior WHERE user_id = #{userId}) " +
            "ORDER BY g.rating_score DESC, g.fans_count DESC LIMIT #{limit}")
    List<RecommendItem> findTopGames(@Param("userId") Long userId, @Param("limit") int limit);
}

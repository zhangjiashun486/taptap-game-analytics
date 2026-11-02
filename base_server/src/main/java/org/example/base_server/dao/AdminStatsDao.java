package org.example.base_server.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 管理端统计查询（只读）。
 * 返回 List<Map> / Map：列名直接作为 JSON 字段，减少重复的实体类。
 */
@Mapper
public interface AdminStatsDao {

    // ---------- 总览 ----------
    @Select("SELECT COUNT(*) FROM t_game")        long countGames();
    @Select("SELECT COUNT(*) FROM t_tag")         long countTags();
    @Select("SELECT COUNT(*) FROM t_game_tag")    long countGameTags();
    @Select("SELECT COUNT(*) FROM t_user")        long countUsers();
    @Select("SELECT COUNT(*) FROM t_behavior")    long countBehaviors();
    @Select("SELECT COUNT(*) FROM t_qa_log")      long countQaLogs();
    @Select("SELECT COUNT(*) FROM t_crawl_log")   long countCrawlLogs();
    @Select("SELECT MAX(run_id) FROM t_cluster_run") Long latestRunId();

    // ---------- 采集统计 ----------
    @Select("SELECT id, batch_id AS batchId, task_type AS taskType, status, total, success, failed, " +
            "       message, started_at AS startedAt, finished_at AS finishedAt " +
            "FROM t_crawl_log ORDER BY id DESC LIMIT 20")
    List<Map<String, Object>> recentCrawlLogs();

    @Select("SELECT status, COUNT(*) AS cnt FROM t_crawl_log GROUP BY status")
    List<Map<String, Object>> crawlStatusCounts();

    // ---------- 聚类统计 ----------
    @Select("SELECT run_id AS runId, algorithm, k_value AS kValue, silhouette, sse, created_at AS createdAt " +
            "FROM t_cluster_run ORDER BY run_id DESC LIMIT 1")
    Map<String, Object> latestClusterRun();

    @Select("SELECT cluster_id AS clusterId, cluster_name AS clusterName, size, keywords " +
            "FROM t_cluster_meta WHERE run_id = #{runId} ORDER BY size DESC")
    List<Map<String, Object>> clusterMeta(@Param("runId") Long runId);

    // ---------- 用户行为统计 ----------
    @Select("SELECT behavior_type AS behaviorType, COUNT(*) AS cnt FROM t_behavior " +
            "GROUP BY behavior_type ORDER BY cnt DESC")
    List<Map<String, Object>> behaviorTypeCounts();

    @Select("SELECT g.game_id AS gameId, g.title, COUNT(*) AS cnt " +
            "FROM t_behavior b JOIN t_game g ON b.game_id = g.game_id " +
            "GROUP BY g.game_id, g.title ORDER BY cnt DESC LIMIT 10")
    List<Map<String, Object>> topGamesByBehavior();

    @Select("SELECT u.id, u.username, u.nickname, COUNT(*) AS cnt " +
            "FROM t_behavior b JOIN t_user u ON b.user_id = u.id " +
            "GROUP BY u.id, u.username, u.nickname ORDER BY cnt DESC LIMIT 10")
    List<Map<String, Object>> activeUsers();

    @Select("SELECT DATE(created_at) AS day, COUNT(*) AS cnt FROM t_behavior " +
            "WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) " +
            "GROUP BY DATE(created_at) ORDER BY day")
    List<Map<String, Object>> behaviorDaily();

    // ---------- 问答统计 ----------
    @Select("SELECT intent, COUNT(*) AS cnt FROM t_qa_log GROUP BY intent ORDER BY cnt DESC")
    List<Map<String, Object>> qaIntentCounts();

    @Select("SELECT question, intent, hit_count AS hitCount, created_at AS createdAt " +
            "FROM t_qa_log ORDER BY id DESC LIMIT 10")
    List<Map<String, Object>> recentQaLogs();
}

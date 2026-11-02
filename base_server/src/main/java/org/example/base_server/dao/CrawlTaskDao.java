package org.example.base_server.dao;

import org.apache.ibatis.annotations.*;
import org.example.base_server.entity.CrawlTask;
import org.example.base_server.entity.CrawlTaskLog;

import java.time.LocalDateTime;
import java.util.List;

/** 采集任务与详细日志的数据库访问。 */
@Mapper
public interface CrawlTaskDao {

    @Insert("INSERT INTO t_crawl_task(task_type, status, progress, stage, message, mode, started_at, created_at, updated_at) " +
            "VALUES(#{taskType}, 'RUNNING', 0, #{stage}, #{message}, #{mode}, NOW(), NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertTask(CrawlTask task);

    @Select("SELECT id, task_type AS taskType, status, progress, stage, message, mode, " +
            "started_at AS startedAt, finished_at AS finishedAt, created_at AS createdAt, updated_at AS updatedAt " +
            "FROM t_crawl_task WHERE id = #{id}")
    CrawlTask findById(@Param("id") Long id);

    @Select("SELECT id, task_type AS taskType, status, progress, stage, message, mode, " +
            "started_at AS startedAt, finished_at AS finishedAt, created_at AS createdAt, updated_at AS updatedAt " +
            "FROM t_crawl_task WHERE task_type = #{taskType} ORDER BY id DESC LIMIT 1")
    CrawlTask findLatestByType(@Param("taskType") String taskType);

    @Select("SELECT id, task_type AS taskType, status, progress, stage, message, mode, " +
            "started_at AS startedAt, finished_at AS finishedAt, created_at AS createdAt, updated_at AS updatedAt " +
            "FROM t_crawl_task WHERE task_type = #{taskType} AND status = 'RUNNING' ORDER BY id DESC LIMIT 1")
    CrawlTask findRunningByType(@Param("taskType") String taskType);

    @Update("UPDATE t_crawl_task SET status = #{status}, progress = #{progress}, stage = #{stage}, " +
            "message = #{message}, finished_at = #{finishedAt}, updated_at = NOW() WHERE id = #{id}")
    int updateTask(CrawlTask task);

    @Insert("INSERT INTO t_crawl_task_log(task_id, level, message, created_at) " +
            "VALUES(#{taskId}, #{level}, #{message}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertLog(CrawlTaskLog log);

    @Select("SELECT id, task_id AS taskId, level, message, created_at AS createdAt " +
            "FROM t_crawl_task_log WHERE task_id = #{taskId} AND id > #{afterId} " +
            "ORDER BY id ASC LIMIT 200")
    List<CrawlTaskLog> listLogs(@Param("taskId") Long taskId, @Param("afterId") Long afterId);

    @Insert("INSERT INTO t_crawl_log(batch_id, task_type, status, total, success, failed, " +
            "started_at, finished_at, message, created_at) " +
            "VALUES(#{batchId}, #{taskType}, #{status}, #{total}, #{success}, #{failed}, " +
            "#{startedAt}, #{finishedAt}, #{message}, NOW())")
    int insertSummary(@Param("batchId") String batchId,
                      @Param("taskType") String taskType,
                      @Param("status") String status,
                      @Param("total") int total,
                      @Param("success") int success,
                      @Param("failed") int failed,
                      @Param("startedAt") LocalDateTime startedAt,
                      @Param("finishedAt") LocalDateTime finishedAt,
                      @Param("message") String message);
}
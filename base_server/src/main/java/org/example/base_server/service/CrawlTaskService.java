package org.example.base_server.service;

import org.example.base_server.entity.CrawlTask;
import org.example.base_server.entity.CrawlTaskLog;

import java.util.List;

/** 采集任务控制服务。 */
public interface CrawlTaskService {

    CrawlTask start(String taskType);

    CrawlTask get(Long taskId);

    CrawlTask latest(String taskType);

    List<CrawlTaskLog> logs(Long taskId, Long afterId);
}
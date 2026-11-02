package org.example.base_server.dto;

import lombok.Data;

/** 启动采集任务请求。 */
@Data
public class CrawlTaskRequest {
    private String taskType;
}
package org.example.base_server.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 采集任务详细日志。 */
@Data
public class CrawlTaskLog {
    private Long id;
    private Long taskId;
    private String level;
    private String message;
    private LocalDateTime createdAt;
}
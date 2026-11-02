package org.example.base_server.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 采集任务控制记录。 */
@Data
public class CrawlTask {
    private Long id;
    private String taskType;
    private String status;
    private Integer progress;
    private String stage;
    private String message;
    private String mode;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
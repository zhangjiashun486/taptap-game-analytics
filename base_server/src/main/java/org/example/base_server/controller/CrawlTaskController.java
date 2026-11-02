package org.example.base_server.controller;

import org.example.base_server.common.Result;
import org.example.base_server.dto.CrawlTaskRequest;
import org.example.base_server.entity.CrawlTask;
import org.example.base_server.entity.CrawlTaskLog;
import org.example.base_server.service.CrawlTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 管理端采集任务控制接口，需要 ADMIN token。 */
@RestController
@RequestMapping("/api/admin/crawl")
public class CrawlTaskController {

    private final CrawlTaskService service;

    public CrawlTaskController(CrawlTaskService service) {
        this.service = service;
    }

    @PostMapping("/tasks")
    public Result<CrawlTask> start(@RequestBody CrawlTaskRequest request) {
        try {
            return Result.success("任务已启动", service.start(request.getTaskType()));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/tasks/latest")
    public Result<CrawlTask> latest(@RequestParam String taskType) {
        try {
            return Result.success(service.latest(taskType));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/tasks/{taskId}")
    public Result<CrawlTask> status(@PathVariable Long taskId) {
        CrawlTask task = service.get(taskId);
        return task == null ? Result.error(404, "任务不存在") : Result.success(task);
    }

    @GetMapping("/tasks/{taskId}/logs")
    public Result<List<CrawlTaskLog>> logs(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") Long afterId) {
        return Result.success(service.logs(taskId, afterId));
    }
}
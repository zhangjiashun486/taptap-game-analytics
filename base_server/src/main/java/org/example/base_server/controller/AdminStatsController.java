package org.example.base_server.controller;

import org.example.base_server.common.Result;
import org.example.base_server.service.AdminStatsService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端统计接口（需要 ADMIN 角色的 token）：
 *   GET /api/admin/stats/overview
 *   GET /api/admin/stats/crawl
 *   GET /api/admin/stats/cluster
 *   GET /api/admin/stats/behavior
 *   GET /api/admin/stats/qa
 */
@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    public AdminStatsController(AdminStatsService adminStatsService) {
        this.adminStatsService = adminStatsService;
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() { return Result.success(adminStatsService.overview()); }

    @GetMapping("/crawl")
    public Result<Map<String, Object>> crawl() { return Result.success(adminStatsService.crawl()); }

    @GetMapping("/cluster")
    public Result<Map<String, Object>> cluster() { return Result.success(adminStatsService.cluster()); }

    @GetMapping("/behavior")
    public Result<Map<String, Object>> behavior() { return Result.success(adminStatsService.behavior()); }

    @GetMapping("/qa")
    public Result<Map<String, Object>> qa() { return Result.success(adminStatsService.qa()); }
}

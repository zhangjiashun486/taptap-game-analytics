package org.example.base_server.controller;

import org.example.base_server.common.Result;
import org.example.base_server.service.DeepSeekService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统健康检查接口（无需登录，属于 JWT 白名单）。
 *   GET /api/health      —— 服务是否存活
 *   GET /api/health/db   —— 数据库是否连得上
 *   GET /api/health/ai   —— DeepSeek 是否已启用（不返回 API Key）
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final DeepSeekService deepSeekService;

    public HealthController(JdbcTemplate jdbcTemplate, DeepSeekService deepSeekService) {
        this.jdbcTemplate = jdbcTemplate;
        this.deepSeekService = deepSeekService;
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("service", "base_server");
        data.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return Result.success(data);
    }

    @GetMapping("/health/db")
    public Result<Map<String, Object>> dbHealth() {
        Map<String, Object> data = new LinkedHashMap<>();
        Integer games = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_game", Integer.class);
        Integer tags = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_tag", Integer.class);
        data.put("status", "ok");
        data.put("t_game", games);
        data.put("t_tag", tags);
        return Result.success(data);
    }

    /** 只显示开关/模型等状态，永远不会返回 API Key。 */
    @GetMapping("/health/ai")
    public Result<Map<String, Object>> aiHealth() {
        return Result.success(deepSeekService.status());
    }
}
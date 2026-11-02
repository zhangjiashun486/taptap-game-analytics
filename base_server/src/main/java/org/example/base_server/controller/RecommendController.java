package org.example.base_server.controller;

import org.example.base_server.common.Result;
import org.example.base_server.service.RecommendService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 个性化推荐接口（需要登录）：
 *   GET /api/recommend?limit=10
 * 逻辑：读取用户行为 → 找同簇/同标签游戏 → 返回推荐 + 理由；无行为则冷启动。
 */
@RestController
@RequestMapping("/api/recommend")
public class RecommendController {

    private final RecommendService recommendService;

    public RecommendController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    @GetMapping
    public Result<Map<String, Object>> recommend(
            @RequestParam(defaultValue = "10") int limit,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(recommendService.recommend(userId, limit));
    }
}

package org.example.base_server.controller;

import org.example.base_server.common.Result;
import org.example.base_server.service.GameService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 游戏接口（需要登录后携带 token 访问）：
 *   GET /api/game/page?page=1&limit=20&keyword=原神&sort=rating
 *   GET /api/game/{gameId}
 */
@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    /** 分页查询游戏列表 */
    @GetMapping("/page")
    public Result<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "default") String sort) {
        return Result.success(gameService.page(page, limit, keyword, sort));
    }

    /** 游戏详情（含标签） */
    @GetMapping("/{gameId}")
    public Result<Map<String, Object>> detail(@PathVariable Long gameId) {
        Map<String, Object> data = gameService.detail(gameId);
        return data == null ? Result.error(404, "游戏不存在") : Result.success(data);
    }
}

package org.example.base_server.controller;

import org.example.base_server.common.Result;
import org.example.base_server.dto.BehaviorRequest;
import org.example.base_server.service.BehaviorService;
import org.example.base_server.vo.BehaviorItem;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户行为接口（需要登录，token 由拦截器校验）：
 *   POST /api/behavior       记录浏览/收藏/点赞/玩过/评分
 *   GET  /api/behavior/mine  查看我的行为记录
 */
@RestController
@RequestMapping("/api/behavior")
public class BehaviorController {

    private final BehaviorService behaviorService;

    public BehaviorController(BehaviorService behaviorService) {
        this.behaviorService = behaviorService;
    }

    @PostMapping
    public Result<Map<String, Object>> record(@RequestBody BehaviorRequest request,
                                              @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        try {
            return Result.success("行为已记录", behaviorService.record(userId, request));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/mine")
    public Result<List<BehaviorItem>> mine(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(behaviorService.listMine(userId));
    }
}

package org.example.base_server.controller;

import org.example.base_server.common.Result;
import org.example.base_server.dto.QaRequest;
import org.example.base_server.service.QaService;
import org.example.base_server.vo.QaAnswer;
import org.springframework.web.bind.annotation.*;

/**
 * 数据问答接口（需要登录）：
 *   POST /api/qa/ask    {"question":"评分 9 分以上的游戏有哪些？"}
 */
@RestController
@RequestMapping("/api/qa")
public class QaController {

    private final QaService qaService;

    public QaController(QaService qaService) {
        this.qaService = qaService;
    }

    @PostMapping("/ask")
    public Result<QaAnswer> ask(@RequestBody QaRequest request,
                                @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return Result.error(400, "问题不能为空");
        }
        return Result.success(qaService.ask(userId, request.getQuestion()));
    }
}

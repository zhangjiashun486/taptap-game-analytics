package org.example.base_server.controller;

import org.example.base_server.common.Result;
import org.example.base_server.dto.LoginRequest;
import org.example.base_server.dto.RegisterRequest;
import org.example.base_server.entity.User;
import org.example.base_server.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** 用户端接口：注册、登录、查看个人信息 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 注册（白名单，无需 token） */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        try {
            User user = userService.register(request);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", user.getId());
            data.put("username", user.getUsername());
            data.put("nickname", user.getNickname());
            data.put("role", user.getRole());
            return Result.success("注册成功", data);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 登录（白名单，无需 token） */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            return Result.success("登录成功", userService.login(request, httpRequest));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 查看当前登录用户（需要 token） */
    @GetMapping("/me")
    public Result<User> me(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        User user = userService.findById(userId);
        return user == null ? Result.error(404, "用户不存在") : Result.success(user);
    }
}

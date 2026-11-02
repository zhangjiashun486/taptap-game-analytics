package org.example.base_server.controller;

import org.example.base_server.common.JwtUtil;
import org.example.base_server.common.Result;
import org.example.base_server.dao.LoginLogDao;
import org.example.base_server.dto.LoginRequest;
import org.example.base_server.entity.LoginLog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端登录：管理员不注册、不入库，
 * 账号密码从 application.properties 的 admin.username / admin.password 读取。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    private final JwtUtil jwtUtil;
    private final LoginLogDao loginLogDao;

    public AdminController(JwtUtil jwtUtil, LoginLogDao loginLogDao) {
        this.jwtUtil = jwtUtil;
        this.loginLogDao = loginLogDao;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        boolean ok = adminUsername.equals(request.getUsername()) && adminPassword.equals(request.getPassword());

        // 无论成功失败都记一条登录日志
        LoginLog entity = new LoginLog();
        entity.setUserId(null);
        entity.setUsername(request.getUsername());
        entity.setRole("ADMIN");
        entity.setLoginIp(httpRequest.getRemoteAddr());
        entity.setUserAgent(httpRequest.getHeader("User-Agent"));
        entity.setSuccess(ok ? 1 : 0);
        entity.setMessage(ok ? "管理员登录成功" : "账号或密码错误");
        loginLogDao.insert(entity);

        if (!ok) {
            return Result.error(400, "账号或密码错误");
        }

        String token = jwtUtil.generate(null, adminUsername, "ADMIN");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("username", adminUsername);
        data.put("role", "ADMIN");
        return Result.success("登录成功", data);
    }
}

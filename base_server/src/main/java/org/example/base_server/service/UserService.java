package org.example.base_server.service;

import org.example.base_server.dto.LoginRequest;
import org.example.base_server.dto.RegisterRequest;
import org.example.base_server.entity.User;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/** 用户业务接口 */
public interface UserService {

    /** 注册：校验用户名唯一、密码长度，密码加密后入库 */
    User register(RegisterRequest request);

    /** 登录：校验密码，成功返回 token + 用户信息，并写登录日志 */
    Map<String, Object> login(LoginRequest request, HttpServletRequest httpRequest);

    /** 按 id 查询用户（返回前清空密码字段） */
    User findById(Long id);
}

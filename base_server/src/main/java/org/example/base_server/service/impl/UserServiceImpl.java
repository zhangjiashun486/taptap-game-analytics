package org.example.base_server.service.impl;

import org.example.base_server.common.JwtUtil;
import org.example.base_server.dao.LoginLogDao;
import org.example.base_server.dao.UserDao;
import org.example.base_server.dto.LoginRequest;
import org.example.base_server.dto.RegisterRequest;
import org.example.base_server.entity.LoginLog;
import org.example.base_server.entity.User;
import org.example.base_server.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    private final UserDao userDao;
    private final LoginLogDao loginLogDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserServiceImpl(UserDao userDao, LoginLogDao loginLogDao,
                           PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userDao = userDao;
        this.loginLogDao = loginLogDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public User register(RegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new IllegalArgumentException("密码至少 6 位");
        }
        String username = request.getUsername().trim();
        if (userDao.findByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));   // BCrypt 哈希
        user.setNickname(request.getNickname() == null || request.getNickname().isBlank()
                ? username : request.getNickname().trim());
        user.setRole("USER");
        user.setStatus(1);
        userDao.insert(user);
        user.setPasswordHash(null);   // 返回前不暴露密码哈希
        return user;
    }

    @Override
    public Map<String, Object> login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userDao.findByUsername(request.getUsername());
        //检查密码
        boolean passwordOk = user != null && passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        if (!passwordOk) {
            String reason = user == null ? "用户不存在" : "密码错误";
            saveLoginLog(user == null ? null : user.getId(), request.getUsername(), "USER", httpRequest, 0, reason);
            throw new IllegalArgumentException(reason);
        }
        //检查账号状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            saveLoginLog(user.getId(), user.getUsername(), user.getRole(), httpRequest, 0, "账号已被禁用");
            throw new IllegalArgumentException("账号已被禁用");
        }

        saveLoginLog(user.getId(), user.getUsername(), user.getRole(), httpRequest, 1, "登录成功");

        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());//把用户编号、用户名、角色交给 JWT 工具，生成一串登录凭据，用变量 token 接住。
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("role", user.getRole());
        return data;
    }

    @Override
    public User findById(Long id) {
        User user = userDao.findById(id);
        if (user != null) {
            user.setPasswordHash(null);
        }
        return user;
    }

    /** 写登录日志（成功/失败都写） */
    private void saveLoginLog(Long userId, String username, String role,
                              HttpServletRequest request, int success, String message) {
        LoginLog entity = new LoginLog();
        entity.setUserId(userId);
        entity.setUsername(username);
        entity.setRole(role);
        entity.setLoginIp(request.getRemoteAddr());
        entity.setUserAgent(request.getHeader("User-Agent"));
        entity.setSuccess(success);
        entity.setMessage(message);
        loginLogDao.insert(entity);
    }
}

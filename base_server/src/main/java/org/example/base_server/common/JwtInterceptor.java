package org.example.base_server.common;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * JWT 拦截器：所有 /api/** 请求先经过这里检查 token。
 * 白名单路径在 WebConfig 中配置（登录、注册、健康检查不需要 token）。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtInterceptor.class);

    private final JwtUtil jwtUtil;

    public JwtInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 静态资源等不是 Controller 方法，直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeUnauthorized(response, "未登录或缺少 token");
            return false;
        }

        try {
            Claims claims = jwtUtil.parse(header.substring(7));   // 去掉 "Bearer "
            Object userId = claims.get("userId");
            request.setAttribute("userId", userId == null ? null : ((Number) userId).longValue());
            request.setAttribute("username", claims.getSubject());
            request.setAttribute("role", claims.get("role"));

            // 管理端接口要求 ADMIN 角色（/api/admin/login 已在白名单里）
            String path = request.getRequestURI();
            if (path.startsWith("/api/admin") && !"ADMIN".equals(claims.get("role"))) {
                writeJson(response, 403, "需要管理员权限");
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("token 校验失败: {}", e.getMessage());
            writeUnauthorized(response, "token 无效或已过期");
            return false;
        }
    }

    /** 返回统一的 401 JSON（和 Result 格式保持一致） */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        writeJson(response, 401, message);
    }

    /** 按指定 HTTP 状态码返回统一 JSON */
    private void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}

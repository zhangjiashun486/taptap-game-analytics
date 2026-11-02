package org.example.base_server.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 登录日志表 t_login_log 的映射对象 */
@Data
public class LoginLog {
    private Long id;
    private Long userId;           // 管理员登录时为空
    private String username;
    private String role;           // USER / ADMIN
    private String loginIp;
    private String userAgent;
    private Integer success;       // 1 成功，0 失败
    private String message;        // 失败原因/成功说明
    private LocalDateTime loginTime;
}

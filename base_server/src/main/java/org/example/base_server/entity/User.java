package org.example.base_server.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户表 t_user 的映射对象 */
@Data
public class User {
    private Long id;
    private String username;
    private String passwordHash;   // 数据库字段 password_hash，存 BCrypt 哈希
    private String nickname;
    private String role;           // USER / ADMIN
    private Integer status;        // 1 正常，0 禁用
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

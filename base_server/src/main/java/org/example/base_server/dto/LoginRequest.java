package org.example.base_server.dto;

import lombok.Data;

/** 登录请求体：前端 POST 的 JSON */
@Data
public class LoginRequest {
    private String username;
    private String password;
}

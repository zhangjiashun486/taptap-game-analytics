package org.example.base_server.dto;

import lombok.Data;

/** 注册请求体 */
@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String nickname;
}

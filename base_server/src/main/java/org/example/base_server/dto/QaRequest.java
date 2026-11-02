package org.example.base_server.dto;

import lombok.Data;

/** 问答请求体 */
@Data
public class QaRequest {
    private String question;   // 用户输入的自然语言问题
}

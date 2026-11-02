package org.example.base_server.entity.deepseek;

import lombok.Data;

/** DeepSeek 返回的错误详情。 */
@Data
public class DeepSeekError {
    private String message;
    private String type;
    private String param;
    private String code;
}
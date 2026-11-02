package org.example.base_server.entity.deepseek;

import lombok.Data;

/** DeepSeek 返回的一个候选回答。 */
@Data
public class DeepSeekChoice {
    private Integer index;
    private DeepSeekMessage message;
    private String finish_reason;
}
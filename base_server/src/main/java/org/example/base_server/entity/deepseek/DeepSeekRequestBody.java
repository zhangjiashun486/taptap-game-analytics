package org.example.base_server.entity.deepseek;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 发送给 DeepSeek /chat/completions 的请求体。 */
@Data
public class DeepSeekRequestBody {
    private String model;
    private List<DeepSeekMessage> messages = new ArrayList<>();
    private Double temperature = 0.3;
    private Integer max_tokens = 800;
    private Boolean stream = false;

    public DeepSeekRequestBody(String model, List<DeepSeekMessage> messages, int maxTokens) {
        this.model = model;
        this.messages = messages;
        this.max_tokens = maxTokens;
    }
}
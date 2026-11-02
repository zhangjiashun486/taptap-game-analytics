package org.example.base_server.entity.deepseek;

import lombok.Data;

import java.util.List;

/** DeepSeek /chat/completions 的响应体。 */
@Data
public class DeepSeekResponseBody {
    private String id;
    private String object;
    private Long created;
    private String model;
    private List<DeepSeekChoice> choices;
    private DeepSeekError error;
    private DeepSeekUsage usage;

    /** 取第一条回答的文本内容。 */
    public String firstContent() {
        if (choices == null || choices.isEmpty() || choices.get(0).getMessage() == null) {
            return null;
        }
        return choices.get(0).getMessage().getContent();
    }
}
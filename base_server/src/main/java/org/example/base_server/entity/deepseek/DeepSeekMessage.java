package org.example.base_server.entity.deepseek;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DeepSeek 对话消息：role = system/user/assistant。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeepSeekMessage {
    private String role;
    private String content;
}
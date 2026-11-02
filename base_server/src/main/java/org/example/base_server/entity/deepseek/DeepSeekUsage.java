package org.example.base_server.entity.deepseek;

import lombok.Data;

/** 本次调用的 token 用量，便于后续统计成本。 */
@Data
public class DeepSeekUsage {
    private Integer prompt_tokens;
    private Integer completion_tokens;
    private Integer total_tokens;
}
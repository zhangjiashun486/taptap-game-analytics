package org.example.base_server.dto;

import lombok.Data;

/** 记录行为的请求体 */
@Data
public class BehaviorRequest {
    private Long gameId;
    private String behaviorType;   // VIEW / FAVORITE / LIKE / PLAYED / RATE
    private Integer score;         // 仅 RATE 需要，1~10
}

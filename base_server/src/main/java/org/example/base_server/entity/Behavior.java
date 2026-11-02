package org.example.base_server.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户行为表 t_behavior 的映射对象 */
@Data
public class Behavior {
    private Long id;
    private Long userId;          // 用户 ID
    private Long gameId;          // 游戏 ID
    private String behaviorType;  // VIEW / FAVORITE / LIKE / PLAYED / RATE
    private Integer score;        // 评分（仅 RATE 使用，1~10）
    private LocalDateTime createdAt;
}

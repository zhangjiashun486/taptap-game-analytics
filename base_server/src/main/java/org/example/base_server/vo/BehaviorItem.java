package org.example.base_server.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 行为列表返回对象（关联查询带出游戏名称，方便前端展示） */
@Data
public class BehaviorItem {
    private Long id;
    private Long gameId;
    private String gameTitle;      // 来自 t_game.title
    private String behaviorType;
    private Integer score;
    private LocalDateTime createdAt;
}

package org.example.base_server.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 推荐结果条目（游戏信息 + 推荐理由） */
@Data
public class RecommendItem {
    private Long gameId;
    private String title;
    private BigDecimal ratingScore;
    private Long fansCount;
    private String iconUrl;
    private String developer;
    private String reason;        // 推荐理由（服务层填充）
    private String clusterName;   // 所属簇名称（服务层填充，冷启动时为空）
}

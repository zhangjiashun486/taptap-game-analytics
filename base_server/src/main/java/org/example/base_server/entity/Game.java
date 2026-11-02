package org.example.base_server.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 游戏表 t_game 的映射对象（字段与数据库列一一对应） */
@Data
public class Game {
    private Long gameId;             // game_id：TapTap 游戏 ID
    private String title;            // title：游戏名称
    private BigDecimal ratingScore;  // rating_score：综合评分
    private BigDecimal latestScore;  // latest_score：最新版评分
    private Integer reviewCount;     // review_count：评论数
    private Long fansCount;          // fans_count：关注数
    private Long hitsTotal;          // hits_total：热度
    private Integer wishCount;       // wish_count：愿望单
    private Integer reserveCount;    // reserve_count：预约数
    private String price;            // price：价格/付费模式
    private String platform;         // platform：平台
    private LocalDateTime releasedTime; // released_time：发行时间
    private String developer;        // developer：厂商
    private String intro;            // intro：简介
    private String iconUrl;          // icon_url：封面地址
}

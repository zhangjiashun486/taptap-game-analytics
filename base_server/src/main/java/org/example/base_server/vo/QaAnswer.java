package org.example.base_server.vo;

import lombok.Data;

import java.util.List;

/** 问答结果：意图、答案、数据、来源和可信状态。 */
@Data
public class QaAnswer {
    private String intent;             // RATING / TOPN / TAG / SIMILAR / COUNT / FALLBACK / SQL_QUERY / GENERAL
    private String answer;             // 组织好的回答
    private int total;                 // 命中数量
    private List<RecommendItem> items; // 相关游戏列表（可为空）
    private String mode;               // RULE / LLM / SQL / SQL_LLM / LLM_GENERAL
    private String source;             // DB / GENERAL / SYSTEM
    private Boolean verified;          // 是否来自数据库验证
    private String warning;            // 未验证提示
    private String sql;                // 只读问数生成的 SQL
}
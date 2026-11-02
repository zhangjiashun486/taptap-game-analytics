package org.example.base_server.vo;

/** DeepSeek 生成的只读查询计划。 */
public record SqlPlan(boolean canAnswer, String sql, String reason) {
}
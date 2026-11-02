package org.example.base_server.vo;

/** 只读 SQL 执行结果。 */
public record SqlQueryResult(String sql, String text, int rowCount, String error) {
    public boolean success() {
        return error == null || error.isBlank();
    }
}
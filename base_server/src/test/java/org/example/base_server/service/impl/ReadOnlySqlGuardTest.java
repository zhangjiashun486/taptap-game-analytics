package org.example.base_server.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 只读问数 SQL 校验器的单元测试。
 * 不启动 Spring、不连数据库：只验证 validateAndNormalize 这一层能否挡住大模型生成的坏 SQL。
 */
class ReadOnlySqlGuardTest {

    private static final String URL = "jdbc:mysql://127.0.0.1:3306/taptap_course";
    private static final String USER = "taptap_ai";
    private static final String PASSWORD = "test";

    private ReadOnlySqlServiceImpl service() {
        return new ReadOnlySqlServiceImpl(true, URL, USER, PASSWORD, 50, 5);
    }

    private ReadOnlySqlServiceImpl serviceWithMaxRows(int maxRows) {
        return new ReadOnlySqlServiceImpl(true, URL, USER, PASSWORD, maxRows, 5);
    }

    private String reject(String sql) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service().validateAndNormalize(sql));
        return ex.getMessage();
    }

    // ---------- 空值与语法入口 ----------

    @Test
    @DisplayName("空 SQL 直接拒绝")
    void nullSqlRejected() {
        assertTrue(reject(null).contains("SQL 为空"));
    }

    @Test
    @DisplayName("全空白 SQL 直接拒绝")
    void blankSqlRejected() {
        assertTrue(reject("   \n\t ").contains("SQL 为空"));
    }

    @Test
    @DisplayName("非 SELECT 语句拒绝：UPDATE")
    void updateRejected() {
        assertTrue(reject("UPDATE t_behavior SET score = 1 WHERE id = 2").contains("只允许 SELECT"));
    }

    @Test
    @DisplayName("非 SELECT 语句拒绝：DELETE")
    void deleteRejected() {
        assertTrue(reject("DELETE FROM t_behavior WHERE id = 1").contains("只允许 SELECT"));
    }

    @Test
    @DisplayName("非 SELECT 语句拒绝：INSERT")
    void insertRejected() {
        assertTrue(reject("INSERT INTO t_game (id) VALUES (1)").contains("只允许 SELECT"));
    }

    @Test
    @DisplayName("非 SELECT 语句拒绝：DROP")
    void dropRejected() {
        assertTrue(reject("DROP TABLE t_game").contains("只允许 SELECT"));
    }

    // ---------- 多语句与注释 ----------

    @Test
    @DisplayName("多语句拼接拒绝")
    void multiStatementRejected() {
        assertTrue(reject("SELECT id FROM t_game; SELECT 1 FROM t_tag").contains("多语句"));
    }

    @Test
    @DisplayName("行注释拒绝")
    void lineCommentRejected() {
        assertTrue(reject("SELECT id FROM t_game -- 注释").contains("注释"));
    }

    @Test
    @DisplayName("块注释拒绝")
    void blockCommentRejected() {
        assertTrue(reject("SELECT id FROM t_game /* 注释 */").contains("注释"));
    }

    @Test
    @DisplayName("井号注释拒绝")
    void hashCommentRejected() {
        assertTrue(reject("SELECT id FROM t_game # 注释").contains("注释"));
    }

    @Test
    @DisplayName("反引号拒绝")
    void backtickRejected() {
        assertTrue(reject("SELECT `id` FROM t_game").contains("转义"));
    }

    @Test
    @DisplayName("反斜杠转义拒绝")
    void backslashRejected() {
        assertTrue(reject("SELECT id FROM t_game WHERE name = 'a\\\\b'").contains("转义"));
    }

    // ---------- 关键字黑名单 ----------

    @Test
    @DisplayName("UNION 注入拒绝")
    void unionRejected() {
        assertTrue(reject("SELECT id FROM t_game UNION SELECT id FROM t_tag").contains("不允许"));
    }

    @Test
    @DisplayName("时间盲注函数 SLEEP 拒绝")
    void sleepRejected() {
        assertTrue(reject("SELECT id FROM t_game WHERE SLEEP(5) = 0").contains("不允许"));
    }

    @Test
    @DisplayName("写文件 INTO OUTFILE 拒绝")
    void intoOutfileRejected() {
        assertTrue(reject("SELECT id FROM t_game INTO OUTFILE '/tmp/x'").contains("不允许"));
    }

    @Test
    @DisplayName("读系统库 INFORMATION_SCHEMA 拒绝")
    void informationSchemaRejected() {
        assertTrue(reject("SELECT table_name FROM information_schema.tables").contains("不允许"));
    }

    // ---------- 通配与表名白名单 ----------

    @Test
    @DisplayName("SELECT * 拒绝")
    void selectStarRejected() {
        assertTrue(reject("SELECT * FROM t_game").contains("SELECT *"));
    }

    @Test
    @DisplayName("表别名下的 SELECT * 也拒绝")
    void qualifiedSelectStarRejected() {
        assertTrue(reject("SELECT g.* FROM t_game g").contains("SELECT *"));
    }

    @Test
    @DisplayName("白名单外的表拒绝：t_user")
    void userTableRejected() {
        assertTrue(reject("SELECT id FROM t_user LIMIT 1").contains("白名单"));
    }

    @Test
    @DisplayName("敏感列名先被关键字黑名单拦下")
    void passwordHashColumnRejected() {
        assertTrue(reject("SELECT password_hash FROM t_game LIMIT 1").contains("不允许"));
    }

    @Test
    @DisplayName("白名单外的表拒绝：t_login_log")
    void loginLogTableRejected() {
        assertTrue(reject("SELECT id FROM t_login_log LIMIT 1").contains("白名单"));
    }

    @Test
    @DisplayName("没有 FROM 子句拒绝")
    void noTableRejected() {
        assertTrue(reject("SELECT 1").contains("未识别到允许查询的表"));
    }

    // ---------- LIMIT 上限 ----------

    @Test
    @DisplayName("LIMIT 超过上限拒绝")
    void limitExceedsMaxRejected() {
        assertTrue(reject("SELECT id FROM t_game LIMIT 100").contains("LIMIT 不能超过 50"));
    }

    @Test
    @DisplayName("LIMIT 恰好等于上限放行")
    void limitEqualsMaxAccepted() {
        String sql = service().validateAndNormalize("SELECT id FROM t_game LIMIT 50");
        assertTrue(sql.endsWith("LIMIT 50"));
    }

    @Test
    @DisplayName("构造参数超过 50 会被夹到 50")
    void maxRowsClampedTo50() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> serviceWithMaxRows(999).validateAndNormalize("SELECT id FROM t_game LIMIT 60"));
        assertTrue(ex.getMessage().contains("50"));
    }

    @Test
    @DisplayName("自定义上限 20 生效")
    void customMaxRowsRespected() {
        ReadOnlySqlServiceImpl svc = serviceWithMaxRows(20);
        assertEquals("SELECT id FROM t_game LIMIT 20", svc.validateAndNormalize("SELECT id FROM t_game LIMIT 20"));
        assertThrows(IllegalArgumentException.class,
                () -> svc.validateAndNormalize("SELECT id FROM t_game LIMIT 21"));
    }

    // ---------- 放行与规范化 ----------

    @Test
    @DisplayName("正常查询自动补 LIMIT")
    void simpleSelectGetsLimit() {
        assertEquals("SELECT id FROM t_game LIMIT 50",
                service().validateAndNormalize("SELECT id FROM t_game"));
    }

    @Test
    @DisplayName("小写 select 放行")
    void lowercaseSelectAccepted() {
        assertDoesNotThrow(() -> service().validateAndNormalize("select id from t_game"));
    }

    @Test
    @DisplayName("结尾分号被剥掉")
    void trailingSemicolonStripped() {
        String sql = service().validateAndNormalize("SELECT id FROM t_game;");
        assertFalse(sql.contains(";"));
        assertTrue(sql.endsWith("LIMIT 50"));
    }

    @Test
    @DisplayName("模型输出的 ```sql 代码围栏被剥掉")
    void markdownFenceStripped() {
        String sql = service().validateAndNormalize("```sql\nSELECT id FROM t_game\n```");
        assertTrue(sql.startsWith("SELECT"));
        assertFalse(sql.contains("```"));
    }

    @Test
    @DisplayName("多余空白被折叠成单空格")
    void whitespaceCollapsed() {
        String sql = service().validateAndNormalize("SELECT   id\n\tFROM    t_game");
        assertEquals("SELECT id FROM t_game LIMIT 50", sql);
    }

    @Test
    @DisplayName("两张白名单表 JOIN 放行")
    void joinWhitelistedTablesAccepted() {
        assertDoesNotThrow(() -> service().validateAndNormalize(
                "SELECT g.id, t.name FROM t_game g JOIN t_game_tag t ON g.id = t.game_id"));
    }

    @Test
    @DisplayName("带别名与 WHERE 的查询放行")
    void aliasAndWhereAccepted() {
        String sql = service().validateAndNormalize(
                "SELECT g.id FROM t_game g WHERE g.score > 8");
        assertTrue(sql.startsWith("SELECT g.id"));
        assertTrue(sql.endsWith("LIMIT 50"));
    }

    @Test
    @DisplayName("聚合查询不会被 SELECT * 规则误伤")
    void aggregateAccepted() {
        assertDoesNotThrow(() -> service().validateAndNormalize(
                "SELECT COUNT(*) AS total FROM t_game"));
    }
}

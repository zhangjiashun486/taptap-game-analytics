package org.example.base_server;

import org.example.base_server.service.ReadOnlySqlService;
import org.example.base_server.vo.SqlQueryResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "TAPTAP_DB_PASSWORD", matches = ".+")
class ReadOnlySqlServiceIntegrationTest {

    @Autowired
    private ReadOnlySqlService readOnlySqlService;

    @Test
    void selectShouldWork() {
        SqlQueryResult result = readOnlySqlService.execute(
                "SELECT COUNT(*) AS total FROM t_game"
        );
        assertTrue(result.success(), result.error());
        assertEquals(1, result.rowCount());
        assertTrue(result.text().contains("10219"));
    }

    @Test
    void updateShouldBeRejected() {
        SqlQueryResult result = readOnlySqlService.execute(
                "UPDATE t_behavior SET score = score WHERE 1 = 0"
        );
        assertFalse(result.success());
        assertTrue(result.error().contains("SELECT"));
    }

    @Test
    void userTableShouldBeRejected() {
        SqlQueryResult result = readOnlySqlService.execute(
                "SELECT password_hash FROM t_user LIMIT 1"
        );
        assertFalse(result.success());
        assertTrue(result.error().contains("白名单") || result.error().contains("不允许"));
    }
}
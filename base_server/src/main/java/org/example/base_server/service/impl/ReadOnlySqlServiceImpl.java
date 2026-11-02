package org.example.base_server.service.impl;

import org.example.base_server.service.ReadOnlySqlService;
import org.example.base_server.vo.SqlQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SQL 安全校验 + 只读数据库账号执行。 */
@Service
public class ReadOnlySqlServiceImpl implements ReadOnlySqlService {

    private static final Logger log = LoggerFactory.getLogger(ReadOnlySqlServiceImpl.class);

    private static final Set<String> ALLOWED_TABLES = Set.of(
            "t_game", "t_tag", "t_game_tag",
            "t_cluster_run", "t_cluster_result", "t_cluster_meta",
            "t_behavior"
    );

    private static final Pattern TABLE_PATTERN =
            Pattern.compile("(?i)\\b(?:FROM|JOIN)\\s+([a-zA-Z0-9_]+)");
    private static final Pattern LIMIT_PATTERN =
            Pattern.compile("(?i)\\bLIMIT\\s+(\\d+)");
    private static final Pattern FORBIDDEN_PATTERN =
            Pattern.compile("(?i)\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|REPLACE|MERGE|" +
                    "GRANT|REVOKE|CALL|EXECUTE|SET|USE|LOCK|UNLOCK|LOAD|OUTFILE|DUMPFILE|SLEEP|BENCHMARK|" +
                    "LOAD_FILE|INFORMATION_SCHEMA|PERFORMANCE_SCHEMA|MYSQL|USER_ID|PASSWORD_HASH|INTO)\\b");

    private final boolean enabled;
    private final String url;
    private final String username;
    private final String password;
    private final int maxRows;
    private final int timeoutSeconds;

    public ReadOnlySqlServiceImpl(
            @Value("${qa.sql-agent.enabled:true}") boolean enabled,
            @Value("${qa.sql-agent.url:jdbc:mysql://192.168.100.101:3306/taptap_course?useUnicode=true&characterEncoding=UTF-8&useSSL=false}") String url,
            @Value("${qa.sql-agent.username:taptap_ai}") String username,
            @Value("${qa.sql-agent.password:}") String password,
            @Value("${qa.sql-agent.max-rows:50}") int maxRows,
            @Value("${qa.sql-agent.timeout-seconds:5}") int timeoutSeconds) {
        this.enabled = enabled;
        this.url = url;
        this.username = username;
        this.password = password == null ? "" : password;
        this.maxRows = Math.max(1, Math.min(maxRows, 50));
        this.timeoutSeconds = Math.max(1, Math.min(timeoutSeconds, 15));
        log.info("只读问数配置：enabled={}, username={}, passwordConfigured={}, maxRows={}",
                enabled, username, !this.password.isBlank(), this.maxRows);
    }

    @Override
    public boolean isAvailable() {
        return enabled && url != null && !url.isBlank()
                && username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }

    @Override
    public SqlQueryResult execute(String rawSql) {
        if (!isAvailable()) {
            return new SqlQueryResult(rawSql, null, 0, "只读问数未配置");
        }

        String sql;
        try {
            sql = validateAndNormalize(rawSql);
        } catch (IllegalArgumentException e) {
            return new SqlQueryResult(rawSql, null, 0, e.getMessage());
        }

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(timeoutSeconds);
                statement.setMaxRows(maxRows);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<Map<String, Object>> rows = readRows(resultSet);
                    String text = formatRows(rows);
                    return new SqlQueryResult(sql, text, rows.size(), null);
                }
            }
        } catch (Exception e) {
            log.warn("只读 SQL 执行失败：{}", e.getMessage());
            return new SqlQueryResult(sql, null, 0, "数据库查询失败");
        }
    }

    String validateAndNormalize(String rawSql) {
        if (rawSql == null || rawSql.isBlank()) {
            throw new IllegalArgumentException("SQL 为空");
        }

        String sql = rawSql.replace("```sql", "")
                .replace("```", "")
                .trim();
        while (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        sql = sql.replaceAll("\\s+", " ").trim();

        if (sql.isEmpty() || !sql.toUpperCase(Locale.ROOT).startsWith("SELECT")) {
            throw new IllegalArgumentException("只允许 SELECT 查询");
        }
        if (sql.contains(";") || sql.contains("--") || sql.contains("/*") || sql.contains("#")) {
            throw new IllegalArgumentException("禁止多语句和注释");
        }
        if (sql.contains("`") || sql.contains("\\")) {
            throw new IllegalArgumentException("禁止特殊转义字符");
        }
        if (FORBIDDEN_PATTERN.matcher(sql).find() || sql.matches("(?is).*\\bUNION\\b.*")) {
            throw new IllegalArgumentException("包含不允许的 SQL 关键字");
        }
        if (sql.matches("(?is)^\\s*SELECT\\s+(?:[a-zA-Z0-9_]+\\.)?\\*.*")) {
            throw new IllegalArgumentException("禁止 SELECT *");
        }

        Matcher tableMatcher = TABLE_PATTERN.matcher(sql);
        boolean hasTable = false;
        while (tableMatcher.find()) {
            hasTable = true;
            String table = tableMatcher.group(1).toLowerCase(Locale.ROOT);
            if (!ALLOWED_TABLES.contains(table)) {
                throw new IllegalArgumentException("表不在白名单中: " + table);
            }
        }
        if (!hasTable) {
            throw new IllegalArgumentException("未识别到允许查询的表");
        }

        Matcher limitMatcher = LIMIT_PATTERN.matcher(sql);
        if (limitMatcher.find()) {
            int requested = Integer.parseInt(limitMatcher.group(1));
            if (requested > maxRows) {
                throw new IllegalArgumentException("LIMIT 不能超过 " + maxRows);
            }
        } else {
            sql = sql + " LIMIT " + maxRows;
        }
        return sql;
    }

    private List<Map<String, Object>> readRows(ResultSet resultSet) throws Exception {
        ResultSetMetaData meta = resultSet.getMetaData();
        int columns = meta.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (resultSet.next() && rows.size() < maxRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columns; i++) {
                String name = meta.getColumnLabel(i);
                Object value = resultSet.getObject(i);
                row.put(name, value);
            }
            rows.add(row);
        }
        return rows;
    }

    private String formatRows(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "查询结果为空";
        }
        StringBuilder text = new StringBuilder();
        int index = 1;
        for (Map<String, Object> row : rows) {
            text.append(index++).append(". ");
            int column = 0;
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (column++ > 0) text.append(" | ");
                text.append(entry.getKey()).append('=').append(entry.getValue());
            }
            text.append('\n');
        }
        return text.toString();
    }
}
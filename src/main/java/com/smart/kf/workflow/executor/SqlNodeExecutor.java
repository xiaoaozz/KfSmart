package com.smart.kf.workflow.executor;

import com.smart.kf.config.ReadOnlyDataSourceProperties;
import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流「SQL 查询」节点。
 *
 * <p>安全模型：
 * <ul>
 *   <li>仅允许 SELECT 查询（解析首关键字，拒绝 DDL/DML/多语句）；</li>
 *   <li>使用独立只读 {@link DataSource}：配置 {@code workflow.sql-node.read-only.*} 时
 *       在构造期自建独立连接池（推荐只读账号，深度防御）；未配置时回退主业务 DataSource；</li>
 *   <li>连接强制只读 ({@code setReadOnly(true)})；</li>
 *   <li>强制查询超时 ({@code setQueryTimeout})，默认 10s，可由
 *       {@code workflow.sql-node.query-timeout-seconds} 配置；</li>
 *   <li>保留 {@code setMaxRows} 行数上限。</li>
 * </ul>
 */
@Component
public class SqlNodeExecutor implements NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SqlNodeExecutor.class);

    private final DataSource dataSource;
    private final int queryTimeoutSeconds;

    public SqlNodeExecutor(DataSource primaryDataSource,
                           ReadOnlyDataSourceProperties readOnlyProperties,
                           @Value("${workflow.sql-node.query-timeout-seconds:10}") int queryTimeoutSeconds) {
        this.dataSource = buildReadOnlyDataSource(primaryDataSource, readOnlyProperties);
        this.queryTimeoutSeconds = Math.max(1, queryTimeoutSeconds);
    }

    /**
     * 构建只读 DataSource：配置了 {@code workflow.sql-node.read-only.url} 时自建独立连接池
     * （推荐只读账号，深度防御）；否则回退主业务 DataSource。
     *
     * <p>不注册独立 {@code DataSource} Bean，避免触发 Spring Boot {@code DataSourceAutoConfiguration}
     * 的 {@code @ConditionalOnMissingBean} 抑制而影响主库装配。
     */
    private static DataSource buildReadOnlyDataSource(DataSource primaryDataSource,
                                                     ReadOnlyDataSourceProperties props) {
        if (props == null || props.getUrl() == null || props.getUrl().isBlank()) {
            logger.info("未配置 workflow.sql-node.read-only.url，SQL 节点回退主 DataSource");
            return primaryDataSource;
        }
        logger.info("为 SQL 节点配置独立只读 DataSource（深度防御，账号应为只读权限）");
        return DataSourceBuilder.create()
                .url(props.getUrl())
                .username(props.getUsername())
                .password(props.getPassword())
                .driverClassName(props.getDriverClassName())
                .build();
    }

    @Override
    public String getNodeType() {
        return "SQL查询";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        String sql = ctx.resolveTemplate(node.configString("sql"));
        String resultType = node.configString("resultType");
        int maxRows = toInt(node.configObject("maxRows"), 100);

        if (sql == null || sql.isBlank()) {
            throw new IllegalStateException("SQL 查询节点未配置 SQL 语句");
        }

        assertSelectOnly(sql);

        logger.info("执行只读 SQL: {}", sql);

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            conn.setReadOnly(true);
            stmt.setQueryTimeout(queryTimeoutSeconds);
            stmt.setMaxRows(maxRows);
            boolean hasResultSet = stmt.execute(sql);
            if (hasResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    int columnCount = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
                        }
                        results.add(row);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL 执行失败: " + e.getMessage(), e);
        }

        Object output = "单值".equals(resultType) && !results.isEmpty()
                ? results.get(0).values().iterator().next()
                : results;

        ctx.setVariableIfAbsent("toolResult", output != null ? output : "");

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("rows", results);
        outputs.put("result", output != null ? output : "");
        return NodeExecutionResult.of(outputs, "执行SQL查询，返回" + results.size() + "行数据");
    }

    /**
     * 仅允许以 SELECT 开头的单条查询，拒绝 DDL/DML 及多语句注入。
     */
    private void assertSelectOnly(String sql) {
        String normalized = stripLeadingCommentsAndWhitespace(sql);
        if (normalized.isEmpty()) {
            throw new IllegalStateException("SQL 语句为空");
        }
        String upper = normalized.toUpperCase();
        if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
            throw new IllegalStateException("SQL 节点仅允许 SELECT / WITH 查询，禁止 " + upper.substring(0, Math.min(10, upper.length())) + "...");
        }
        // 拒绝多语句（除非处于引号/注释内，简化处理：检测裸分号）
        if (containsBareSemicolon(sql)) {
            throw new IllegalStateException("SQL 节点禁止多语句（检测到语句分隔符）");
        }
    }

    private static String stripLeadingCommentsAndWhitespace(String sql) {
        String s = sql;
        while (true) {
            s = s.trim();
            if (s.startsWith("--")) {
                int nl = s.indexOf('\n');
                if (nl < 0) {
                    return "";
                }
                s = s.substring(nl + 1);
            } else if (s.startsWith("/*")) {
                int end = s.indexOf("*/");
                if (end < 0) {
                    return "";
                }
                s = s.substring(end + 2);
            } else {
                return s;
            }
        }
    }

    private static boolean containsBareSemicolon(String sql) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            switch (c) {
                case '\'' -> { if (!inDouble) inSingle = !inSingle; }
                case '"' -> { if (!inSingle) inDouble = !inDouble; }
                case ';' -> { if (!inSingle && !inDouble) return true; }
                default -> { /* continue */ }
            }
        }
        return false;
    }

    @SuppressWarnings("sameParameterValue")
    private int toInt(Object val, int defaultValue) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception ignored) {}
        }
        return defaultValue;
    }
}

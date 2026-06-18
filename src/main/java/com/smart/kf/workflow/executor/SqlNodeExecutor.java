package com.smart.kf.workflow.executor;

import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SqlNodeExecutor implements NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SqlNodeExecutor.class);

    private final DataSource dataSource;

    public SqlNodeExecutor(DataSource dataSource) {
        this.dataSource = dataSource;
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

        logger.info("执行 SQL: {}", sql);

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
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
        } catch (Exception e) {
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

    @SuppressWarnings("sameParameterValue")
    private int toInt(Object val, int defaultValue) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception ignored) {}
        }
        return defaultValue;
    }
}

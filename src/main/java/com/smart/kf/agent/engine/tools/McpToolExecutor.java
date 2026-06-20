package com.smart.kf.agent.engine.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.agent.engine.ToolResult;
import com.smart.kf.service.McpToolInvocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class McpToolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(McpToolExecutor.class);

    private final McpToolInvocationService invocationService;
    private final ObjectMapper objectMapper;

    public McpToolExecutor(McpToolInvocationService invocationService, ObjectMapper objectMapper) {
        this.invocationService = invocationService;
        this.objectMapper = objectMapper;
    }

    public ToolResult execute(String toolName, Map<String, Object> arguments) {
        try {
            Object result = invocationService.execute(toolName, arguments);
            String output = objectMapper.writeValueAsString(result);
            logger.info("MCP 工具调用完成: tool={}", toolName);
            return ToolResult.success(output);
        } catch (Exception e) {
            logger.error("MCP 工具调用失败: tool={}, error={}", toolName, e.getMessage(), e);
            return ToolResult.failure("工具调用失败: " + e.getMessage());
        }
    }
}

package com.smart.kf.workflow.executor;

import com.smart.kf.service.agent.AgentExecutionService;
import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AgentCallNodeExecutor implements NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(AgentCallNodeExecutor.class);

    private final AgentExecutionService agentExecutionService;

    @Autowired
    public AgentCallNodeExecutor(AgentExecutionService agentExecutionService) {
        this.agentExecutionService = agentExecutionService;
    }

    @Override
    public String getNodeType() {
        return "Agent调用";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        String agentId = node.configString("agentId");
        String query = node.configString("query");
        if (query == null || query.isBlank()) {
            query = String.valueOf(ctx.getOrDefault("query", ""));
        }

        logger.info("Agent 调用: agentId={}, queryLen={}", agentId, query.length());

        Map<String, Object> agentResult = agentExecutionService.chat(agentId, query);

        Map<String, Object> outputs = Map.of(
            "output", agentResult.getOrDefault("answer", ""),
            "agentId", agentId != null ? agentId : "",
            "iterations", agentResult.getOrDefault("iterations", 0),
            "executionId", agentResult.getOrDefault("executionId", "")
        );
        return NodeExecutionResult.of(outputs, "调用Agent[" + (agentId != null ? agentId : "unknown") + "]，迭代" + outputs.get("iterations") + "次");
    }
}

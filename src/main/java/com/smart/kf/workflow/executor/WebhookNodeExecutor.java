package com.smart.kf.workflow.executor;

import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WebhookNodeExecutor implements NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(WebhookNodeExecutor.class);

    @Override
    public String getNodeType() {
        return "Webhook";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        logger.info("Webhook 节点暂未接入实际请求，返回 stub 结果");

        String url = node.configString("url");
        String method = node.configString("method");

        Map<String, Object> outputs = Map.of(
            "url", url != null ? url : "",
            "method", method != null ? method : "POST",
            "status", "not_implemented"
        );
        return NodeExecutionResult.of(outputs, "Webhook[" + (method != null ? method : "POST") + "] " + (url != null ? url : "") + "（暂未实现）");
    }
}

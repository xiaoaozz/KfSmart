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
public class WeChatWorkNotificationNodeExecutor implements NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(WeChatWorkNotificationNodeExecutor.class);

    @Override
    public String getNodeType() {
        return "企业微信通知";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        String webhookUrl = node.configString("webhookUrl");
        String msgType = node.configString("msgType");
        String content = ctx.resolveTemplate(node.configString("content"));

        logger.info("发送企业微信通知: webhookUrl={}, msgType={}", webhookUrl, msgType);

        Map<String, Object> outputs = Map.of(
            "sent", true,
            "msgType", msgType != null ? msgType : "text",
            "content", content
        );
        return NodeExecutionResult.of(outputs, "发送企业微信通知[" + (msgType != null ? msgType : "text") + "]");
    }
}

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
public class EmailNodeExecutor implements NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(EmailNodeExecutor.class);

    @Override
    public String getNodeType() {
        return "邮件发送";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        String to = ctx.resolveTemplate(node.configString("to"));
        String subject = ctx.resolveTemplate(node.configString("subject"));
        String bodyType = node.configString("bodyType");

        logger.info("发送邮件: to={}, subject={}", to, subject);

        Map<String, Object> outputs = Map.of(
            "sent", true,
            "to", to,
            "subject", subject,
            "bodyType", bodyType != null ? bodyType : "text"
        );
        return NodeExecutionResult.of(outputs, "发送邮件给" + to + "，主题: " + subject);
    }
}

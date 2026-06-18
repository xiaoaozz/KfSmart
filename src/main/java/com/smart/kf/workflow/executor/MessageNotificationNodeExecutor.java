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
public class MessageNotificationNodeExecutor implements NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(MessageNotificationNodeExecutor.class);

    @Override
    public String getNodeType() {
        return "消息通知";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        String channel = node.configString("channel");
        String recipients = ctx.resolveTemplate(node.configString("recipients"));
        String title = ctx.resolveTemplate(node.configString("title"));
        String content = ctx.resolveTemplate(node.configString("content"));
        String priority = node.configString("priority");

        logger.info("发送消息通知: channel={}, recipients={}, title={}, priority={}", channel, recipients, title, priority);

        // 实际场景中根据 channel 调用不同通知服务
        // 当前实现：记录日志并返回结果
        Map<String, Object> outputs = Map.of(
            "sent", true,
            "channel", channel != null ? channel : "系统通知",
            "recipients", recipients,
            "title", title,
            "content", content
        );
        return NodeExecutionResult.of(outputs, "发送消息通知[" + (channel != null ? channel : "系统通知") + "]给" + recipients + "，标题: " + title);
    }
}

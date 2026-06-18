package com.smart.kf.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * 工作流 WebSocket 处理器。
 * <p>
 * 前端连接 /ws/workflow/{token} 后发送 {type:"subscribe", executionId:"exec_xxx"} 订阅执行进度。
 */
@Component
public class WorkflowWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowWebSocketHandler.class);

    private final WorkflowProgressBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public WorkflowWebSocketHandler(WorkflowProgressBroadcaster broadcaster, ObjectMapper objectMapper) {
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(@Nullable WebSocketSession session) {
        logger.info("工作流 WebSocket 连接建立: {}", session != null ? session.getId() : "null");
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleTextMessage(@Nullable WebSocketSession session, @Nullable TextMessage message) {
        if (session == null || message == null) {
            return;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");
            String executionId = (String) payload.get("executionId");

            if ("subscribe".equals(type) && executionId != null) {
                broadcaster.subscribe(executionId, session);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "subscribed",
                    "executionId", executionId
                ))));
            } else if ("unsubscribe".equals(type) && executionId != null) {
                broadcaster.unsubscribe(executionId, session);
            }
        } catch (Exception e) {
            logger.warn("处理工作流 WebSocket 消息失败: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(@Nullable WebSocketSession session, @Nullable CloseStatus status) {
        logger.info("工作流 WebSocket 连接关闭: {}, status={}", session != null ? session.getId() : "null", status);
    }
}

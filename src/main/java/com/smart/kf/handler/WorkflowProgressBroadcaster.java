package com.smart.kf.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 工作流执行进度广播器。
 * <p>
 * 维护 executionId → WebSocket sessions 的映射。
 * 异步执行时，每完成一个节点，通过此广播器推送进度到前端。
 */
@Component
public class WorkflowProgressBroadcaster {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowProgressBroadcaster.class);

    private final Map<String, List<WebSocketSession>> subscriptions = new ConcurrentHashMap<>();

    public void subscribe(String executionId, WebSocketSession session) {
        subscriptions.computeIfAbsent(executionId, k -> new CopyOnWriteArrayList<>()).add(session);
        logger.info("WebSocket 订阅执行进度: executionId={}, sessionCount={}", executionId,
            subscriptions.get(executionId).size());
    }

    public void unsubscribe(String executionId, WebSocketSession session) {
        List<WebSocketSession> sessions = subscriptions.get(executionId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                subscriptions.remove(executionId);
            }
        }
    }

    /**
     * 向订阅了指定 executionId 的所有 session 广播消息。
     */
    public void broadcast(String executionId, String message) {
        List<WebSocketSession> sessions = subscriptions.get(executionId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                sendMessage(session, message);
            }
        }
    }

    private void sendMessage(WebSocketSession session, String message) {
        synchronized (session) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                logger.warn("广播消息失败: sessionId={}, error={}", session.getId(), e.getMessage());
            }
        }
    }
}

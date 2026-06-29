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
 * <p>
 * 内置消息缓冲：如果执行线程在 WebSocket 订阅前就已开始（典型竞态），
 * 广播的消息会先缓存，待客户端订阅时回放，避免丢失早期节点的进度通知。
 */
@Component
public class WorkflowProgressBroadcaster {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowProgressBroadcaster.class);

    private final Map<String, List<WebSocketSession>> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, List<String>> messageBuffer = new ConcurrentHashMap<>();

    public void subscribe(String executionId, WebSocketSession session) {
        subscriptions.computeIfAbsent(executionId, k -> new CopyOnWriteArrayList<>()).add(session);
        logger.info("WebSocket 订阅执行进度: executionId={}, sessionCount={}", executionId,
            subscriptions.get(executionId).size());

        // 回放已缓冲的消息（解决执行先于订阅的竞态）
        List<String> buffered = messageBuffer.get(executionId);
        if (buffered != null && !buffered.isEmpty()) {
            logger.info("回放缓冲消息: executionId={}, count={}", executionId, buffered.size());
            for (String msg : buffered) {
                sendMessage(session, msg);
            }
        }
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
     * 同时缓存消息，供后续订阅的客户端回放。
     */
    public void broadcast(String executionId, String message) {
        // 缓存消息
        messageBuffer.computeIfAbsent(executionId, k -> new CopyOnWriteArrayList<>()).add(message);

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

    /**
     * 清理指定 executionId 的消息缓冲。
     * 在执行结束后调用，避免内存泄漏。
     */
    public void cleanup(String executionId) {
        messageBuffer.remove(executionId);
        subscriptions.remove(executionId);
    }

    private void sendMessage(WebSocketSession session, String message) {
        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            logger.warn("广播消息失败: sessionId={}, error={}", session.getId(), e.getMessage());
        }
    }
}

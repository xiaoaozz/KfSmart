package com.smart.kf.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.service.ChatHandler;
import com.smart.kf.utils.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final ChatHandler chatHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtUtils jwtUtils;

    // 内部指令令牌 - 可以从配置文件读取
    private static final String INTERNAL_CMD_TOKEN = "WSS_STOP_CMD_" + System.currentTimeMillis() % 1000000;

    public ChatWebSocketHandler(ChatHandler chatHandler, JwtUtils jwtUtils) {
        this.chatHandler = chatHandler;
        this.jwtUtils = jwtUtils;
    }

    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        String userId = extractUserId(session);
        logger.info("WebSocket连接已建立，用户ID: {}，会话ID: {}，URI路径: {}",
                userId, session.getId(), getSessionPath(session));

        try {
            Map<String, String> connectionMessage = Map.of(
                    "type", "connection",
                    "sessionId", session.getId(),
                    "message", "WebSocket连接已建立"
            );
            String jsonMessage = objectMapper.writeValueAsString(connectionMessage);
            session.sendMessage(new TextMessage(jsonMessage));
            logger.info("已发送会话ID到前端: sessionId={}", session.getId());
        } catch (Exception e) {
            logger.error("发送会话ID失败: {}", e.getMessage(), e);
        }
    }

    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        String userId = extractUserId(session);
        try {
            String payload = message.getPayload();
            logger.info("接收到消息，用户ID: {}，会话ID: {}，消息长度: {}",
                    userId, session.getId(), payload.length());

            if ("ping".equals(payload)) {
                session.sendMessage(new TextMessage("pong"));
                return;
            }

            String messageText = payload;
            String conversationId = null;
            Long apiKeyConfigId = null;

            if (payload.trim().startsWith("{")) {
                try {
                    Map<String, Object> jsonMessage = objectMapper.readValue(
                            payload,
                            new TypeReference<>() {
                            }
                    );
                    String messageType = (String) jsonMessage.get("type");
                    String internalToken = (String) jsonMessage.get("_internal_cmd_token");

                    if ("stop".equals(messageType) && INTERNAL_CMD_TOKEN.equals(internalToken)) {
                        logger.info("收到有效的停止按钮指令，用户ID: {}，会话ID: {}", userId, session.getId());
                        chatHandler.stopResponse(userId, session);
                        return;
                    }

                    if ("chat".equals(messageType)) {
                        Object rawMessage = jsonMessage.get("message");
                        messageText = rawMessage == null ? "" : String.valueOf(rawMessage);
                        Object rawConversationId = jsonMessage.get("conversationId");
                        conversationId = rawConversationId == null ? null : String.valueOf(rawConversationId);
                        Object rawApiKeyConfigId = jsonMessage.get("apiKeyConfigId");
                        if (rawApiKeyConfigId != null) {
                            try {
                                apiKeyConfigId = Long.parseLong(String.valueOf(rawApiKeyConfigId));
                            } catch (NumberFormatException nfe) {
                                logger.warn("无效的 apiKeyConfigId: {}", rawApiKeyConfigId);
                            }
                        }
                        logger.debug("收到JSON格式聊天消息，conversationId={}, apiKeyConfigId={}", conversationId, apiKeyConfigId);
                    } else {
                        logger.debug("收到JSON格式的非聊天消息，当作普通消息处理");
                    }
                } catch (Exception jsonParseError) {
                    logger.debug("JSON解析失败，当作普通消息处理: {}", jsonParseError.getMessage());
                }
            }

            chatHandler.processMessage(userId, messageText, conversationId, apiKeyConfigId, session);

        } catch (Exception e) {
            logger.error("处理消息出错，用户ID: {}，会话ID: {}，错误: {}",
                    userId, session.getId(), e.getMessage(), e);
            sendErrorMessage(session, "消息处理失败：" + e.getMessage());
        }
    }

    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        String userId = extractUserId(session);
        logger.info("WebSocket连接已关闭，用户ID: {}，会话ID: {}，状态: {}",
                userId, session.getId(), status);

        chatHandler.clearSessionState(session.getId());
    }

    private String extractUserId(WebSocketSession session) {
        String path = getSessionPath(session);
        String[] segments = path.split("/");
        String jwtToken = segments[segments.length - 1];

        String username = jwtUtils.extractUsernameFromToken(jwtToken);
        if (username == null) {
            logger.warn("无法从JWT令牌中提取用户名，使用令牌作为用户ID: {}", jwtToken);
            return jwtToken;
        }

        logger.debug("从JWT令牌中提取的用户名: {}", username);
        return username;
    }

    private String getSessionPath(WebSocketSession session) {
        URI uri = session.getUri();
        return uri != null && uri.getPath() != null ? uri.getPath() : "";
    }

    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        try {
            Map<String, String> error = Map.of("error", errorMessage);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
            logger.info("已发送错误消息到会话: {}, 错误: {}", session.getId(), errorMessage);
        } catch (Exception e) {
            logger.error("发送错误消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取内部指令令牌 - 供前端调用
     */
    public static String getInternalCmdToken() {
        return INTERNAL_CMD_TOKEN;
    }
}

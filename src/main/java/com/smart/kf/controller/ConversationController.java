package com.smart.kf.controller;

import com.smart.kf.exception.CustomException;
import com.smart.kf.model.User;
import com.smart.kf.repository.UserRepository;
import com.smart.kf.service.ChatHandler;
import com.smart.kf.utils.JwtUtils;
import com.smart.kf.utils.LogUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users/conversation")
public class ConversationController {

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final ChatHandler chatHandler;

    public ConversationController(UserRepository userRepository, JwtUtils jwtUtils, ChatHandler chatHandler) {
        this.userRepository = userRepository;
        this.jwtUtils = jwtUtils;
        this.chatHandler = chatHandler;
    }

    /**
     * 查询指定会话的消息历史；未传 conversation_id 时返回当前会话。
     */
    @GetMapping
    public ResponseEntity<?> getConversations(
            @RequestHeader("Authorization") String token,
            @RequestParam(name = "conversation_id", required = false) String conversationId) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_CONVERSATIONS");
        String username = null;
        try {
            User user = getCurrentUser(token);
            username = user.getUsername();
            String userId = buildPrimaryUserKey(user);

            ensureLegacyConversationIndex(user);

            String targetConversationId = conversationId;
            if (targetConversationId == null || targetConversationId.trim().isEmpty()) {
                targetConversationId = chatHandler.getCurrentConversationId(userId);
            }

            List<Map<String, Object>> messages = new ArrayList<>();
            if (targetConversationId != null && !targetConversationId.trim().isEmpty()) {
                if (!chatHandler.isConversationOwnedByUser(userId, targetConversationId)) {
                    throw new CustomException("会话不存在或无权访问", HttpStatus.NOT_FOUND);
                }
                messages = chatHandler.getConversationMessages(userId, targetConversationId);
            }

            LogUtils.logBusiness("GET_CONVERSATIONS", username, "查询会话消息成功, conversationId=%s, count=%d",
                    targetConversationId, messages.size());
            monitor.end("获取对话历史成功");
            return ResponseEntity.ok(successResponse("获取对话历史成功", messages));

        } catch (CustomException e) {
            LogUtils.logBusinessError("GET_CONVERSATIONS", username, "获取对话历史失败: %s", e, e.getMessage());
            monitor.end("获取对话历史失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_CONVERSATIONS", username, "获取对话历史异常: %s", e, e.getMessage());
            monitor.end("获取对话历史异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "服务器内部错误: " + e.getMessage()));
        }
    }

    /**
     * 查询当前用户的会话列表（摘要信息）
     */
    @GetMapping("/sessions")
    public ResponseEntity<?> getSessions(@RequestHeader("Authorization") String token,
                                         @RequestParam(name = "session_type", required = false) String sessionType,
                                         @RequestParam(name = "target_type", required = false) String targetType,
                                         @RequestParam(name = "target_id", required = false) String targetId) {
        String username = null;
        try {
            User user = getCurrentUser(token);
            username = user.getUsername();
            String userId = buildPrimaryUserKey(user);

            ensureLegacyConversationIndex(user);
            List<Map<String, Object>> sessions = chatHandler.getConversationSessions(userId, sessionType, targetType, targetId);

            LogUtils.logBusiness("GET_SESSIONS", username, "获取会话列表成功, count=%d", sessions.size());
            return ResponseEntity.ok(successResponse("获取会话列表成功", sessions));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_SESSIONS", username, "获取会话列表异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "服务器内部错误: " + e.getMessage()));
        }
    }

    /**
     * 创建新会话
     */
    @PostMapping("/sessions")
    public ResponseEntity<?> createSession(@RequestHeader("Authorization") String token,
                                           @RequestBody(required = false) Map<String, Object> body) {
        String username = null;
        try {
            User user = getCurrentUser(token);
            username = user.getUsername();
            String userId = buildPrimaryUserKey(user);

            ensureLegacyConversationIndex(user);
            Map<String, Object> session = chatHandler.createConversationSession(userId, body);

            LogUtils.logBusiness("CREATE_SESSION", username, "创建新会话成功, conversationId=%s", session.get("id"));
            return ResponseEntity.ok(successResponse("创建会话成功", session));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("CREATE_SESSION", username, "创建会话异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "服务器内部错误: " + e.getMessage()));
        }
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<?> deleteSession(@RequestHeader("Authorization") String token,
                                           @RequestParam("conversation_id") String conversationId) {
        String username = null;
        try {
            User user = getCurrentUser(token);
            username = user.getUsername();
            String userId = buildPrimaryUserKey(user);

            ensureLegacyConversationIndex(user);
            Map<String, Object> result = chatHandler.deleteConversationSession(userId, conversationId);

            LogUtils.logBusiness("DELETE_SESSION", username, "删除会话成功, conversationId=%s", conversationId);
            return ResponseEntity.ok(successResponse("删除会话成功", result));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("DELETE_SESSION", username, "删除会话异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "服务器内部错误: " + e.getMessage()));
        }
    }

    @PutMapping("/sessions/pin")
    public ResponseEntity<?> pinSession(@RequestHeader("Authorization") String token,
                                        @RequestBody Map<String, Object> body) {
        String username = null;
        try {
            User user = getCurrentUser(token);
            username = user.getUsername();
            String userId = buildPrimaryUserKey(user);
            String conversationId = String.valueOf(body.getOrDefault("conversationId", ""));
            boolean pinned = Boolean.parseBoolean(String.valueOf(body.getOrDefault("pinned", true)));

            ensureLegacyConversationIndex(user);
            Map<String, Object> result = chatHandler.updateConversationPinned(userId, conversationId, pinned);

            LogUtils.logBusiness("PIN_SESSION", username, "更新会话置顶成功, conversationId=%s, pinned=%s", conversationId, pinned);
            return ResponseEntity.ok(successResponse(pinned ? "置顶成功" : "取消置顶成功", result));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("PIN_SESSION", username, "更新会话置顶异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "服务器内部错误: " + e.getMessage()));
        }
    }

    private User getCurrentUser(String token) {
        String username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        if (username == null || username.isEmpty()) {
            throw new CustomException("无效的token", HttpStatus.UNAUTHORIZED);
        }

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("用户不存在", HttpStatus.NOT_FOUND));
    }

    private void ensureLegacyConversationIndex(User user) {
        chatHandler.ensureLegacyConversationIndex(buildPrimaryUserKey(user), buildPossibleUserIds(user));
    }

    private String buildPrimaryUserKey(User user) {
        return user.getUsername();
    }

    private List<String> buildPossibleUserIds(User user) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            candidates.add(user.getUsername());
        }
        if (user.getId() != null) {
            candidates.add(String.valueOf(user.getId()));
            candidates.add(user.getId().toString());
        }
        return new ArrayList<>(candidates);
    }

    private Map<String, Object> successResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", message);
        response.put("data", data);
        return response;
    }
}

package com.smart.kf.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.exception.CustomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 对话历史与会话元数据管理服务，从 {@link ChatHandler} 中提取。
 * 负责 Redis 中会话索引/元数据/历史消息的读写，{@link ChatHandler} 保留同名委托方法。
 */
@Service
public class ConversationHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationHistoryService.class);
    private static final Duration CONVERSATION_TTL = Duration.ofDays(7);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CHAT_SESSION_SCOPE = "chat";

    private final RedisTemplate<String, String> redisTemplate;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    public ConversationHistoryService(RedisTemplate<String, String> redisTemplate,
                                       ConversationService conversationService,
                                       ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
    }

    public void ensureLegacyConversationIndex(String userId, List<String> legacyUserIds) {
        boolean hasCurrentData = !getUserConversationIds(userId, CHAT_SESSION_SCOPE).isEmpty()
                || hasText(redisTemplate.opsForValue().get(userCurrentConversationKey(userId, CHAT_SESSION_SCOPE)));
        if (hasCurrentData) {
            return;
        }

        for (String legacyUserId : legacyUserIds) {
            if (!hasText(legacyUserId)) {
                continue;
            }

            List<String> legacyConversationIds = getConversationIdsFromKey(userConversationIdsKey(legacyUserId));
            if (!legacyConversationIds.isEmpty()) {
                for (int i = legacyConversationIds.size() - 1; i >= 0; i--) {
                    String conversationId = legacyConversationIds.get(i);
                    Map<String, Object> meta = getConversationMeta(conversationId);
                    if (isChatSession(meta)) {
                        attachConversationToUser(userId, conversationId);
                    }
                }
            }

            String legacyCurrentConversationId = redisTemplate.opsForValue().get(userCurrentConversationKey(legacyUserId));
            if (hasText(legacyCurrentConversationId) && isChatSession(getConversationMeta(legacyCurrentConversationId))) {
                attachConversationToUser(userId, legacyCurrentConversationId);
            }

            if (!getUserConversationIds(userId, CHAT_SESSION_SCOPE).isEmpty()
                    || hasText(redisTemplate.opsForValue().get(userCurrentConversationKey(userId, CHAT_SESSION_SCOPE)))) {
                logger.info("已为用户 {} 迁移旧会话索引，来源用户键: {}", userId, legacyUserId);
                return;
            }
        }
    }

    public String getCurrentConversationId(String userId) {
        return getCurrentConversationId(userId, CHAT_SESSION_SCOPE);
    }

    private String getCurrentConversationId(String userId, Map<String, Object> metadata) {
        return getCurrentConversationId(userId, sessionScope(metadata));
    }

    private String getCurrentConversationId(String userId, String scope) {
        String currentConversationId = redisTemplate.opsForValue().get(userCurrentConversationKey(userId, scope));
        if (hasText(currentConversationId)) {
            if (matchesSessionScope(getConversationMeta(currentConversationId), scope)) {
                attachConversationToUser(userId, currentConversationId);
                return currentConversationId;
            }
            redisTemplate.delete(userCurrentConversationKey(userId, scope));
        }

        List<String> conversationIds = getUserConversationIds(userId, scope);
        for (String conversationId : conversationIds) {
            if (matchesSessionScope(getConversationMeta(conversationId), scope)) {
                redisTemplate.opsForValue().set(userCurrentConversationKey(userId, scope), conversationId, CONVERSATION_TTL);
                return conversationId;
            }
        }

        return null;
    }

    public boolean isConversationOwnedByUser(String userId, String conversationId) {
        if (!hasText(conversationId)) {
            return false;
        }

        Map<String, Object> sessionMeta = getConversationMeta(conversationId);
        if (userId.equals(toStringValue(sessionMeta.get("userId"), ""))) {
            return true;
        }

        String scope = sessionScope(sessionMeta);
        String currentConversationId = redisTemplate.opsForValue().get(userCurrentConversationKey(userId, scope));
        if (conversationId.equals(currentConversationId)) {
            return true;
        }

        return getUserConversationIds(userId, scope).contains(conversationId)
                || getUserConversationIds(userId, CHAT_SESSION_SCOPE).contains(conversationId);
    }

    public List<Map<String, Object>> getConversationMessages(String userId, String conversationId) {
        if (!hasText(conversationId) || !isConversationOwnedByUser(userId, conversationId)) {
            return new ArrayList<>();
        }

        attachConversationToUser(userId, conversationId);
        List<Map<String, String>> history = getConversationHistory(conversationId);
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Map<String, String> message : history) {
            Map<String, Object> formattedMessage = new HashMap<>();
            formattedMessage.put("role", message.get("role"));
            formattedMessage.put("content", message.get("content"));
            formattedMessage.put("timestamp", message.get("timestamp"));
            // 返回 status 和 errorMessage 字段，供前端正确渲染错误状态
            if (message.containsKey("status")) {
                formattedMessage.put("status", message.get("status"));
            }
            if (message.containsKey("errorMessage")) {
                formattedMessage.put("errorMessage", message.get("errorMessage"));
            }
            messages.add(formattedMessage);
        }
        return messages;
    }

    public List<Map<String, Object>> getConversationSessions(String userId, String sessionType, String targetType, String targetId) {
        String scope = sessionScope(sessionType, targetType, targetId);
        List<String> conversationIds = getUserConversationIds(userId, scope);
        List<String> legacyConversationIds = CHAT_SESSION_SCOPE.equals(scope)
                ? conversationIds
                : getUserConversationIds(userId, CHAT_SESSION_SCOPE);
        for (String legacyConversationId : legacyConversationIds) {
            if (conversationIds.contains(legacyConversationId)) {
                continue;
            }
            Map<String, Object> legacyMeta = getConversationMeta(legacyConversationId);
            if (matchesSessionFilters(legacyMeta, sessionType, targetType, targetId)) {
                conversationIds.add(legacyConversationId);
                attachConversationToUser(userId, legacyConversationId);
            }
        }

        String currentConversationId = redisTemplate.opsForValue().get(userCurrentConversationKey(userId, scope));
        if (hasText(currentConversationId) && !conversationIds.contains(currentConversationId)) {
            Map<String, Object> currentMeta = getConversationMeta(currentConversationId);
            if (matchesSessionFilters(currentMeta, sessionType, targetType, targetId)) {
                conversationIds.add(0, currentConversationId);
                saveUserConversationIds(userId, scope, conversationIds);
            } else {
                redisTemplate.delete(userCurrentConversationKey(userId, scope));
            }
        }

        List<Map<String, Object>> sessions = new ArrayList<>();
        for (String conversationId : conversationIds) {
            Map<String, Object> sessionMeta = getConversationMeta(conversationId);
            if (sessionMeta.isEmpty()) {
                sessionMeta = buildConversationMetaFromHistory(userId, conversationId, getConversationHistory(conversationId));
                saveConversationMeta(conversationId, sessionMeta);
            }
            if (matchesSessionFilters(sessionMeta, sessionType, targetType, targetId)) {
                sessions.add(normalizeSessionMeta(conversationId, sessionMeta));
            }
        }

        sessions.sort((a, b) -> compareSessionMeta(a, b, currentConversationId));
        return sessions;
    }

    public Map<String, Object> createConversationSession(String userId, Map<String, Object> metadata) {
        String currentConversationId = getCurrentConversationId(userId, metadata);
        if (hasText(currentConversationId)
                && isConversationEmpty(currentConversationId)
                && currentConversationMatches(currentConversationId, metadata)) {
            throw new CustomException("当前新会话暂无消息，请先发送消息后再创建新会话", HttpStatus.BAD_REQUEST);
        }

        String conversationId = createConversationInternal(userId, metadata);
        return normalizeSessionMeta(conversationId, getConversationMeta(conversationId));
    }

    public Map<String, Object> getConversationSessionMeta(String userId, String conversationId) {
        if (!hasText(conversationId) || !isConversationOwnedByUser(userId, conversationId)) {
            return new LinkedHashMap<>();
        }
        return normalizeSessionMeta(conversationId, getConversationMeta(conversationId));
    }

    public void appendConversationTurn(String userId, String conversationId, String userMessage, String response) {
        if (!hasText(conversationId) || !isConversationOwnedByUser(userId, conversationId)) {
            throw new CustomException("会话不存在或无权访问", HttpStatus.NOT_FOUND);
        }
        updateConversationHistory(conversationId, userId, userMessage, response);
    }

    public void appendConversationError(String userId, String conversationId, String userMessage, String errorMessage) {
        if (!hasText(conversationId) || !isConversationOwnedByUser(userId, conversationId)) {
            throw new CustomException("会话不存在或无权访问", HttpStatus.NOT_FOUND);
        }
        saveErrorMessage(conversationId, userId, userMessage, errorMessage);
    }

    public void truncateConversationHistory(String userId, String conversationId, int keepCount) {
        if (!hasText(conversationId) || !isConversationOwnedByUser(userId, conversationId)) {
            throw new CustomException("会话不存在或无权操作", HttpStatus.NOT_FOUND);
        }
        List<Map<String, String>> history = getConversationHistory(conversationId);
        int safeKeep = Math.max(0, Math.min(keepCount, history.size()));
        if (safeKeep == history.size()) return;
        List<Map<String, String>> truncated = new ArrayList<>(history.subList(0, safeKeep));
        try {
            String key = conversationHistoryKey(conversationId);
            String json = objectMapper.writeValueAsString(truncated);
            redisTemplate.opsForValue().set(key, json, CONVERSATION_TTL);
            logger.info("截断会话历史: conversationId={}, keepCount={}, 原有={}", conversationId, safeKeep, history.size());
        } catch (JsonProcessingException e) {
            logger.error("截断会话历史序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("截断失败", e);
        }
    }

    public Map<String, Object> deleteConversationSession(String userId, String conversationId) {
        if (!hasText(conversationId) || !isConversationOwnedByUser(userId, conversationId)) {
            throw new CustomException("会话不存在或无权删除", HttpStatus.NOT_FOUND);
        }

        List<String> conversationIds;
        Map<String, Object> sessionMeta = getConversationMeta(conversationId);
        String scope = sessionScope(sessionMeta);
        conversationIds = getUserConversationIds(userId, scope);
        conversationIds.removeIf(conversationId::equals);
        saveUserConversationIds(userId, scope, conversationIds);

        List<String> legacyConversationIds = getUserConversationIds(userId, CHAT_SESSION_SCOPE);
        if (!CHAT_SESSION_SCOPE.equals(scope) && legacyConversationIds.removeIf(conversationId::equals)) {
            saveUserConversationIds(userId, CHAT_SESSION_SCOPE, legacyConversationIds);
        }

        redisTemplate.delete(conversationHistoryKey(conversationId));
        redisTemplate.delete(conversationMetaKey(conversationId));

        String currentConversationId = redisTemplate.opsForValue().get(userCurrentConversationKey(userId, scope));
        String nextConversationId = null;
        if (conversationId.equals(currentConversationId)) {
            if (!conversationIds.isEmpty()) {
                nextConversationId = conversationIds.get(0);
                redisTemplate.opsForValue().set(userCurrentConversationKey(userId, scope), nextConversationId, CONVERSATION_TTL);
            } else {
                redisTemplate.delete(userCurrentConversationKey(userId, scope));
            }
        } else if (hasText(currentConversationId)) {
            nextConversationId = currentConversationId;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deletedConversationId", conversationId);
        result.put("currentConversationId", nextConversationId == null ? "" : nextConversationId);
        result.put("remainingCount", conversationIds.size());
        return result;
    }

    public Map<String, Object> updateConversationPinned(String userId, String conversationId, boolean pinned) {
        if (!hasText(conversationId) || !isConversationOwnedByUser(userId, conversationId)) {
            throw new CustomException("会话不存在或无权操作", HttpStatus.NOT_FOUND);
        }

        Map<String, Object> sessionMeta = getConversationMeta(conversationId);
        if (sessionMeta.isEmpty()) {
            sessionMeta = buildConversationMetaFromHistory(userId, conversationId, getConversationHistory(conversationId));
        }

        String pinnedAt = pinned ? currentTimestamp() : "";
        sessionMeta.put("isPinned", pinned);
        sessionMeta.put("pinnedAt", pinnedAt);
        saveConversationMeta(conversationId, sessionMeta);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversationId", conversationId);
        result.put("isPinned", pinned);
        result.put("pinnedAt", pinnedAt);
        return result;
    }

    /**
     * 解析请求中的业务会话 ID：优先使用请求指定的会话（校验归属），
     * 否则回退到当前会话，最终新建。供 {@link ChatHandler#processMessage} 调用。
     */
    public String resolveConversationId(String userId, String requestedConversationId) {
        if (hasText(requestedConversationId)) {
            if (isConversationOwnedByUser(userId, requestedConversationId)) {
                attachConversationToUser(userId, requestedConversationId);
                return requestedConversationId;
            }
            logger.warn("用户 {} 尝试访问不属于自己的会话 {}，将回退到当前会话", userId, requestedConversationId);
        }

        String currentConversationId = getCurrentConversationId(userId);
        if (hasText(currentConversationId)) {
            return currentConversationId;
        }

        return createConversationInternal(userId, null);
    }

    private String createConversationInternal(String userId, Map<String, Object> metadata) {
        String conversationId = UUID.randomUUID().toString();
        String now = currentTimestamp();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", conversationId);
        meta.put("userId", userId);
        meta.put("title", "新会话");
        meta.put("lastMessage", "");
        meta.put("lastRole", "");
        meta.put("time", now);
        meta.put("messageCount", 0);
        meta.put("createdAt", now);
        meta.put("updatedAt", now);
        meta.put("isPinned", false);
        meta.put("pinnedAt", "");
        applyExtraSessionMeta(meta, metadata);

        saveConversationMeta(conversationId, meta);
        attachConversationToUser(userId, conversationId);
        logger.info("为用户 {} 创建新的业务会话ID: {}", userId, conversationId);
        return conversationId;
    }

    private void attachConversationToUser(String userId, String conversationId) {
        if (!hasText(userId) || !hasText(conversationId)) {
            return;
        }

        Map<String, Object> sessionMeta = getConversationMeta(conversationId);
        String scope = sessionScope(sessionMeta);
        List<String> conversationIds = getUserConversationIds(userId, scope);
        conversationIds.removeIf(conversationId::equals);
        conversationIds.add(0, conversationId);
        saveUserConversationIds(userId, scope, conversationIds);
        redisTemplate.opsForValue().set(userCurrentConversationKey(userId, scope), conversationId, CONVERSATION_TTL);

        if (sessionMeta.isEmpty()) {
            saveConversationMeta(conversationId, buildConversationMetaFromHistory(userId, conversationId, getConversationHistory(conversationId)));
        } else {
            redisTemplate.expire(conversationMetaKey(conversationId), CONVERSATION_TTL);
        }
        redisTemplate.expire(conversationHistoryKey(conversationId), CONVERSATION_TTL);
    }

    private List<String> getUserConversationIds(String userId, String scope) {
        return getConversationIdsFromKey(userConversationIdsKey(userId, scope));
    }

    private List<String> getConversationIdsFromKey(String key) {
        String json = redisTemplate.opsForValue().get(key);
        if (!hasText(json)) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            logger.error("解析会话索引失败: {}, key={}", e.getMessage(), key, e);
            return new ArrayList<>();
        }
    }

    private void saveUserConversationIds(String userId, String scope, List<String> conversationIds) {
        try {
            String json = objectMapper.writeValueAsString(conversationIds);
            redisTemplate.opsForValue().set(userConversationIdsKey(userId, scope), json, CONVERSATION_TTL);
        } catch (JsonProcessingException e) {
            logger.error("序列化会话索引失败: {}, userId={}", e.getMessage(), userId, e);
        }
    }

    private Map<String, Object> getConversationMeta(String conversationId) {
        String json = redisTemplate.opsForValue().get(conversationMetaKey(conversationId));
        if (!hasText(json)) {
            return new LinkedHashMap<>();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            logger.error("解析会话元数据失败: {}, conversationId={}", e.getMessage(), conversationId, e);
            return new LinkedHashMap<>();
        }
    }

    private void saveConversationMeta(String conversationId, Map<String, Object> sessionMeta) {
        try {
            String json = objectMapper.writeValueAsString(sessionMeta);
            redisTemplate.opsForValue().set(conversationMetaKey(conversationId), json, CONVERSATION_TTL);
        } catch (JsonProcessingException e) {
            logger.error("序列化会话元数据失败: {}, conversationId={}", e.getMessage(), conversationId, e);
        }
    }

    private Map<String, Object> buildConversationMetaFromHistory(String userId, String conversationId, List<Map<String, String>> history) {
        String now = currentTimestamp();
        String title = "新会话";
        String lastMessage = "";
        String lastRole = "";
        String createdAt = now;
        String updatedAt = now;

        if (!history.isEmpty()) {
            String firstTimestamp = history.get(0).get("timestamp");
            createdAt = hasText(firstTimestamp) ? firstTimestamp : now;

            for (Map<String, String> message : history) {
                if ("user".equals(message.get("role")) && hasText(message.get("content"))) {
                    title = truncate(message.get("content"), 30);
                    break;
                }
            }

            Map<String, String> lastHistoryMessage = history.get(history.size() - 1);
            lastMessage = truncate(lastHistoryMessage.getOrDefault("content", ""), 50);
            lastRole = lastHistoryMessage.getOrDefault("role", "");
            String lastTimestamp = lastHistoryMessage.get("timestamp");
            updatedAt = hasText(lastTimestamp) ? lastTimestamp : now;
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", conversationId);
        meta.put("userId", userId);
        meta.put("title", title);
        meta.put("lastMessage", lastMessage);
        meta.put("lastRole", lastRole);
        meta.put("time", updatedAt);
        meta.put("messageCount", history.size());
        meta.put("createdAt", createdAt);
        meta.put("updatedAt", updatedAt);
        meta.put("isPinned", false);
        meta.put("pinnedAt", "");
        return meta;
    }

    private Map<String, Object> normalizeSessionMeta(String conversationId, Map<String, Object> meta) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("id", conversationId);
        normalized.put("title", toStringValue(meta.get("title"), "新会话"));
        normalized.put("lastMessage", toStringValue(meta.get("lastMessage"), ""));
        normalized.put("lastRole", toStringValue(meta.get("lastRole"), ""));
        normalized.put("time", toStringValue(meta.get("time"), toStringValue(meta.get("updatedAt"), "")));
        normalized.put("messageCount", toIntValue(meta.get("messageCount")));
        normalized.put("createdAt", toStringValue(meta.get("createdAt"), ""));
        normalized.put("updatedAt", toStringValue(meta.get("updatedAt"), toStringValue(meta.get("time"), "")));
        normalized.put("isPinned", toBooleanValue(meta.get("isPinned")));
        normalized.put("pinnedAt", toStringValue(meta.get("pinnedAt"), ""));
        normalized.put("sessionType", toStringValue(meta.get("sessionType"), ""));
        normalized.put("targetType", toStringValue(meta.get("targetType"), ""));
        normalized.put("targetId", toStringValue(meta.get("targetId"), ""));
        normalized.put("targetName", toStringValue(meta.get("targetName"), ""));
        normalized.put("targetDescription", toStringValue(meta.get("targetDescription"), ""));
        return normalized;
    }

    private boolean matchesSessionFilters(Map<String, Object> meta, String sessionType, String targetType, String targetId) {
        if (!hasText(sessionType) && !hasText(targetType) && !hasText(targetId)) {
            return isChatSession(meta);
        }
        return matchesMetaValue(meta.get("sessionType"), sessionType)
                && matchesMetaValue(meta.get("targetType"), targetType)
                && matchesMetaValue(meta.get("targetId"), targetId);
    }

    private boolean matchesMetaValue(Object actual, String expected) {
        if (!hasText(expected)) {
            return true;
        }
        return expected.equals(toStringValue(actual, ""));
    }

    private boolean currentConversationMatches(String conversationId, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return true;
        }
        Map<String, Object> currentMeta = getConversationMeta(conversationId);
        return matchesMetaValue(currentMeta.get("sessionType"), toStringValue(metadata.get("sessionType"), ""))
                && matchesMetaValue(currentMeta.get("targetType"), toStringValue(metadata.get("targetType"), ""))
                && matchesMetaValue(currentMeta.get("targetId"), toStringValue(metadata.get("targetId"), ""));
    }

    private boolean isChatSession(Map<String, Object> meta) {
        return meta == null || !hasText(toStringValue(meta.get("sessionType"), ""));
    }

    private boolean matchesSessionScope(Map<String, Object> meta, String scope) {
        if (CHAT_SESSION_SCOPE.equals(scope)) {
            return isChatSession(meta);
        }
        return scope.equals(sessionScope(meta));
    }

    private String sessionScope(Map<String, Object> meta) {
        if (meta == null || meta.isEmpty()) {
            return CHAT_SESSION_SCOPE;
        }
        return sessionScope(
                toStringValue(meta.get("sessionType"), ""),
                toStringValue(meta.get("targetType"), ""),
                toStringValue(meta.get("targetId"), "")
        );
    }

    private String sessionScope(String sessionType, String targetType, String targetId) {
        if (!hasText(sessionType)) {
            return CHAT_SESSION_SCOPE;
        }

        StringBuilder scope = new StringBuilder(sessionType.trim());
        if (hasText(targetType)) {
            scope.append(':').append(targetType.trim());
        }
        if (hasText(targetId)) {
            scope.append(':').append(targetId.trim());
        }
        return scope.toString();
    }

    private void applyExtraSessionMeta(Map<String, Object> targetMeta, Map<String, Object> extraMeta) {
        if (extraMeta == null || extraMeta.isEmpty()) {
            return;
        }

        copyIfPresent(targetMeta, extraMeta, "sessionType");
        copyIfPresent(targetMeta, extraMeta, "targetType");
        copyIfPresent(targetMeta, extraMeta, "targetId");
        copyIfPresent(targetMeta, extraMeta, "targetName");
        copyIfPresent(targetMeta, extraMeta, "targetDescription");
    }

    private void copyIfPresent(Map<String, Object> targetMeta, Map<String, Object> sourceMeta, String key) {
        String value = toStringValue(sourceMeta.get(key), "");
        if (hasText(value)) {
            targetMeta.put(key, value);
        }
    }

    private Map<String, Object> mergeSessionMeta(Map<String, Object> rebuiltMeta, Map<String, Object> existingMeta) {
        Map<String, Object> merged = new LinkedHashMap<>(rebuiltMeta);
        if (existingMeta == null || existingMeta.isEmpty()) {
            return merged;
        }

        merged.put("isPinned", toBooleanValue(existingMeta.get("isPinned")));
        merged.put("pinnedAt", toStringValue(existingMeta.get("pinnedAt"), ""));
        applyExtraSessionMeta(merged, existingMeta);
        return merged;
    }

    private int compareSessionMeta(Map<String, Object> a, Map<String, Object> b, String currentConversationId) {
        String aId = String.valueOf(a.getOrDefault("id", ""));
        String bId = String.valueOf(b.getOrDefault("id", ""));
        boolean aCurrent = hasText(currentConversationId) && currentConversationId.equals(aId);
        boolean bCurrent = hasText(currentConversationId) && currentConversationId.equals(bId);
        if (aCurrent != bCurrent) {
            return aCurrent ? -1 : 1;
        }

        boolean aPinned = toBooleanValue(a.get("isPinned"));
        boolean bPinned = toBooleanValue(b.get("isPinned"));
        if (aPinned != bPinned) {
            return aPinned ? -1 : 1;
        }

        if (aPinned) {
            int pinnedCompare = toStringValue(b.get("pinnedAt"), "").compareTo(toStringValue(a.get("pinnedAt"), ""));
            if (pinnedCompare != 0) {
                return pinnedCompare;
            }
        }

        String bUpdatedAt = toStringValue(b.get("updatedAt"), toStringValue(b.get("time"), ""));
        String aUpdatedAt = toStringValue(a.get("updatedAt"), toStringValue(a.get("time"), ""));
        int updatedCompare = bUpdatedAt.compareTo(aUpdatedAt);
        if (updatedCompare != 0) {
            return updatedCompare;
        }

        String bCreatedAt = toStringValue(b.get("createdAt"), "");
        String aCreatedAt = toStringValue(a.get("createdAt"), "");
        return bCreatedAt.compareTo(aCreatedAt);
    }

    private boolean isConversationEmpty(String conversationId) {
        Map<String, Object> sessionMeta = getConversationMeta(conversationId);
        if (!sessionMeta.isEmpty() && toIntValue(sessionMeta.get("messageCount")) > 0) {
            return false;
        }

        return getConversationHistory(conversationId).isEmpty();
    }

    /**
     * 读取会话历史消息，供 {@link ChatHandler#processMessage} 调用。
     */
    public List<Map<String, String>> getConversationHistory(String conversationId) {
        String key = conversationHistoryKey(conversationId);
        String json = redisTemplate.opsForValue().get(key);
        try {
            if (!hasText(json)) {
                logger.debug("会话 {} 没有历史记录", conversationId);
                return new ArrayList<>();
            }

            List<Map<String, String>> history = objectMapper.readValue(json, new TypeReference<>() {
            });
            logger.debug("读取到会话 {} 的 {} 条历史记录", conversationId, history.size());
            return history;
        } catch (JsonProcessingException e) {
            logger.error("解析对话历史出错: {}, 会话ID: {}", e.getMessage(), conversationId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 发生错误时保存消息历史，assistant 消息携带 status=error 和 errorMessage 字段。
     * 供 {@link ChatHandler#handleStreamError} 调用。
     */
    public void saveErrorMessage(String conversationId, String userId, String userMessage, String errorMessage) {
        String key = conversationHistoryKey(conversationId);
        List<Map<String, String>> history = getConversationHistory(conversationId);

        String currentTimestamp = currentTimestamp();

        Map<String, String> userMsgMap = new HashMap<>();
        userMsgMap.put("role", "user");
        userMsgMap.put("content", userMessage);
        userMsgMap.put("timestamp", currentTimestamp);
        history.add(userMsgMap);

        Map<String, String> assistantMsgMap = new HashMap<>();
        assistantMsgMap.put("role", "assistant");
        assistantMsgMap.put("content", "");
        assistantMsgMap.put("status", "error");
        assistantMsgMap.put("errorMessage", errorMessage);
        assistantMsgMap.put("timestamp", currentTimestamp);
        history.add(assistantMsgMap);

        if (history.size() > 20) {
            history = new ArrayList<>(history.subList(history.size() - 20, history.size()));
        }

        try {
            String json = objectMapper.writeValueAsString(history);
            redisTemplate.opsForValue().set(key, json, CONVERSATION_TTL);
            Map<String, Object> existingMeta = getConversationMeta(conversationId);
            saveConversationMeta(conversationId, mergeSessionMeta(
                    buildConversationMetaFromHistory(userId, conversationId, history),
                    existingMeta
            ));
            attachConversationToUser(userId, conversationId);
            logger.info("已保存错误消息到会话历史，会话ID: {}", conversationId);
        } catch (JsonProcessingException e) {
            logger.error("序列化错误消息历史出错: {}, 会话ID: {}", e.getMessage(), conversationId, e);
        }
    }

    /**
     * 追加一轮用户/助手消息并持久化，供 {@link ChatHandler#finalizeStreamResponse} 调用。
     */
    public void updateConversationHistory(String conversationId, String userId, String userMessage, String response) {
        String key = conversationHistoryKey(conversationId);
        List<Map<String, String>> history = getConversationHistory(conversationId);

        String currentTimestamp = currentTimestamp();

        Map<String, String> userMsgMap = new HashMap<>();
        userMsgMap.put("role", "user");
        userMsgMap.put("content", userMessage);
        userMsgMap.put("timestamp", currentTimestamp);
        history.add(userMsgMap);

        Map<String, String> assistantMsgMap = new HashMap<>();
        assistantMsgMap.put("role", "assistant");
        assistantMsgMap.put("content", response);
        assistantMsgMap.put("timestamp", currentTimestamp);
        history.add(assistantMsgMap);

        if (history.size() > 20) {
            history = new ArrayList<>(history.subList(history.size() - 20, history.size()));
        }

        try {
            String json = objectMapper.writeValueAsString(history);
            redisTemplate.opsForValue().set(key, json, CONVERSATION_TTL);
            Map<String, Object> existingMeta = getConversationMeta(conversationId);
            saveConversationMeta(conversationId, mergeSessionMeta(
                    buildConversationMetaFromHistory(userId, conversationId, history),
                    existingMeta
            ));
            attachConversationToUser(userId, conversationId);
            logger.debug("更新会话历史，会话ID: {}, 总消息数: {}", conversationId, history.size());
        } catch (JsonProcessingException e) {
            logger.error("序列化对话历史出错: {}, 会话ID: {}", e.getMessage(), conversationId, e);
        }
    }

    private String conversationHistoryKey(String conversationId) {
        return "conversation:" + conversationId;
    }

    private String conversationMetaKey(String conversationId) {
        return "conversation:" + conversationId + ":meta";
    }

    private String userConversationIdsKey(String userId) {
        return userConversationIdsKey(userId, CHAT_SESSION_SCOPE);
    }

    private String userConversationIdsKey(String userId, String scope) {
        if (CHAT_SESSION_SCOPE.equals(scope)) {
            return "user:" + userId + ":conversation_ids";
        }
        return "user:" + userId + ":conversation_ids:" + scope;
    }

    /**
     * 构建用户当前会话的 Redis 键，供 {@link ChatHandler#finalizeStreamResponse} 打日志使用。
     */
    public String userCurrentConversationKey(String userId) {
        return userCurrentConversationKey(userId, CHAT_SESSION_SCOPE);
    }

    private String userCurrentConversationKey(String userId, String scope) {
        if (CHAT_SESSION_SCOPE.equals(scope)) {
            return "user:" + userId + ":current_conversation";
        }
        return "user:" + userId + ":current_conversation:" + scope;
    }

    private String currentTimestamp() {
        return LocalDateTime.now().format(TIMESTAMP_FORMATTER);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String truncate(String value, int maxLength) {
        if (!hasText(value)) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
    }

    private String toStringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private int toIntValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                logger.warn("会话消息数格式非法: {}", text);
            }
        }
        return 0;
    }

    private boolean toBooleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return false;
    }
}

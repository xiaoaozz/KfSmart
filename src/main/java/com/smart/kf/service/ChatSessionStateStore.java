package com.smart.kf.service;

import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 聊天 WebSocket 会话的运行态存储，从 {@link ChatHandler} 中提取，
 * 封装原先散落的 7 个 ConcurrentHashMap，仅暴露语义化方法。
 */
@Component
public class ChatSessionStateStore {

    public enum StreamTerminalState {
        ACTIVE,
        COMPLETED,
        STOPPED,
        FAILED
    }

    // 用于存储每个会话的完整响应
    private final Map<String, StringBuilder> responseBuilders = new ConcurrentHashMap<>();
    // 用于跟踪每个会话的响应完成状态
    private final Map<String, CompletableFuture<String>> responseFutures = new ConcurrentHashMap<>();
    // 用于记录每个会话当前流式响应的终态
    private final Map<String, StreamTerminalState> streamTerminalStates = new ConcurrentHashMap<>();
    // 用于存储每个会话的流式订阅，便于主动取消上游请求
    private final Map<String, Disposable> streamSubscriptions = new ConcurrentHashMap<>();
    // 用于存储每个会话的引用映射：sessionId -> {referenceNumber -> fileMd5}
    private final Map<String, Map<Integer, String>> sessionReferenceMappings = new ConcurrentHashMap<>();
    // 用于给每个会话的 sendMessage 加锁，Spring WebSocket session 不支持并发写
    private final Map<String, Object> sessionSendLocks = new ConcurrentHashMap<>();
    // 用于标记每个会话是否已经发送过错误消息，防止 completion 覆盖
    private final Map<String, AtomicBoolean> sessionErrorSent = new ConcurrentHashMap<>();

    /**
     * 初始化会话为 ACTIVE 终态，并重置错误发送标记。
     */
    public void initSession(String sessionId) {
        streamTerminalStates.put(sessionId, StreamTerminalState.ACTIVE);
        sessionErrorSent.put(sessionId, new AtomicBoolean(false));
    }

    public void markState(String sessionId, StreamTerminalState state) {
        streamTerminalStates.put(sessionId, state);
    }

    public StreamTerminalState getState(String sessionId) {
        return streamTerminalStates.get(sessionId);
    }

    public boolean isInactive(String sessionId) {
        return streamTerminalStates.getOrDefault(sessionId, StreamTerminalState.ACTIVE) != StreamTerminalState.ACTIVE;
    }

    public void clearState(String sessionId) {
        streamTerminalStates.remove(sessionId);
    }

    public void clearErrorSent(String sessionId) {
        sessionErrorSent.remove(sessionId);
    }

    public void recordSubscription(String sessionId, Disposable subscription) {
        streamSubscriptions.put(sessionId, subscription);
    }

    /**
     * 取消并移除会话的上游流式订阅。
     *
     * @return 是否实际执行了 dispose（用于调用方决定是否打日志）
     */
    public boolean cancelSubscription(String sessionId) {
        Disposable subscription = streamSubscriptions.remove(sessionId);
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            return true;
        }
        return false;
    }

    /**
     * 为会话创建全新的响应构建器与 CompletableFuture，并返回该 Future。
     */
    public CompletableFuture<String> registerFuture(String sessionId) {
        responseBuilders.put(sessionId, new StringBuilder());
        CompletableFuture<String> future = new CompletableFuture<>();
        responseFutures.put(sessionId, future);
        return future;
    }

    public void appendChunk(String sessionId, String chunk) {
        StringBuilder builder = responseBuilders.get(sessionId);
        if (builder != null) {
            builder.append(chunk);
        }
    }

    /**
     * 取出并移除会话累积的响应内容。
     */
    public String consumeResponse(String sessionId) {
        StringBuilder builder = responseBuilders.remove(sessionId);
        return builder == null ? "" : builder.toString();
    }

    public void completeFuture(String sessionId, String content) {
        CompletableFuture<String> future = responseFutures.remove(sessionId);
        if (future != null && !future.isDone()) {
            future.complete(content);
        }
    }

    public void completeFutureExceptionally(String sessionId, Throwable error) {
        CompletableFuture<String> future = responseFutures.remove(sessionId);
        if (future != null && !future.isDone()) {
            future.completeExceptionally(error);
        }
    }

    public void recordReference(String sessionId, Map<Integer, String> referenceMapping) {
        sessionReferenceMappings.put(sessionId, referenceMapping);
    }

    public Object getLock(String sessionId) {
        return sessionSendLocks.computeIfAbsent(sessionId, k -> new Object());
    }

    public boolean isErrorSent(String sessionId) {
        AtomicBoolean errorSent = sessionErrorSent.get(sessionId);
        return errorSent != null && errorSent.get();
    }

    /**
     * 原子地将错误发送标记从 false 置为 true。
     *
     * @return 若此次调用完成了置位（即调用方应负责发送错误消息）则为 true；
     *         若会话不存在或已被置位过则为 false。
     */
    public boolean markErrorSent(String sessionId) {
        AtomicBoolean errorSent = sessionErrorSent.get(sessionId);
        return errorSent != null && errorSent.compareAndSet(false, true);
    }

    /**
     * 清理结果摘要，供调用方记录日志（字段含义对应原 ChatHandler.clearSessionState 的日志参数）。
     */
    public record ClearResult(int referencesRemoved, int responseLength, boolean futureRemoved,
                               StreamTerminalState terminalState, boolean subscriptionRemoved, boolean lockRemoved) {
    }

    /**
     * 统一清理会话所有状态。修复原实现中两处遗漏：
     * sessionErrorSent 未被清理、responseFutures 用 get 而非 remove（导致已完成的 Future 残留在 Map 中）。
     */
    public ClearResult clear(String sessionId) {
        Map<Integer, String> removedReferences = sessionReferenceMappings.remove(sessionId);
        String partialResponse = consumeResponse(sessionId);
        StreamTerminalState removedTerminalState = streamTerminalStates.remove(sessionId);
        Object removedLock = sessionSendLocks.remove(sessionId);
        sessionErrorSent.remove(sessionId);

        Disposable subscription = streamSubscriptions.remove(sessionId);
        boolean subscriptionRemoved = subscription != null;
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }

        CompletableFuture<String> future = responseFutures.remove(sessionId);
        boolean futureRemoved = future != null;
        if (future != null && !future.isDone()) {
            future.completeExceptionally(new java.util.concurrent.CancellationException("WebSocket session closed: " + sessionId));
        }

        return new ClearResult(
                removedReferences != null ? removedReferences.size() : 0,
                partialResponse.length(),
                futureRemoved,
                removedTerminalState,
                subscriptionRemoved,
                removedLock != null
        );
    }
}

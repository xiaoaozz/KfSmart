package com.smart.kf.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationHistoryServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private ConversationService conversationService;

    private ConversationHistoryService historyService;

    private static final String USER_ID = "alice";
    private static final String CONV_ID = "conv-001";
    private static final String HISTORY_KEY = "conversation:" + CONV_ID;
    private static final String META_KEY = "conversation:" + CONV_ID + ":meta";
    private static final String CURRENT_KEY = "user:" + USER_ID + ":current_conversation";
    private static final String USER_IDS_KEY = "user:" + USER_ID + ":conversation_ids";

    private static final String HISTORY_4_JSON =
            "[{\"role\":\"user\",\"content\":\"q1\",\"timestamp\":\"2024-01-01 10:00:00\"},"
            + "{\"role\":\"assistant\",\"content\":\"a1\",\"timestamp\":\"2024-01-01 10:00:01\"},"
            + "{\"role\":\"user\",\"content\":\"q2\",\"timestamp\":\"2024-01-01 10:01:00\"},"
            + "{\"role\":\"assistant\",\"content\":\"a2\",\"timestamp\":\"2024-01-01 10:01:01\"}]";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        historyService = new ConversationHistoryService(redisTemplate, conversationService, new ObjectMapper());
    }

    private void stubOwnedByAlice() {
        when(valueOps.get(META_KEY)).thenReturn("{\"userId\":\"alice\"}");
    }

    // ---- truncateConversationHistory ----

    @Test
    void truncate_keepCount2_writesFirst2Messages() throws Exception {
        stubOwnedByAlice();
        when(valueOps.get(HISTORY_KEY)).thenReturn(HISTORY_4_JSON);

        historyService.truncateConversationHistory(USER_ID, CONV_ID, 2);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(HISTORY_KEY), jsonCaptor.capture(), any(Duration.class));
        List<?> written = new ObjectMapper().readValue(jsonCaptor.getValue(), List.class);
        assertEquals(2, written.size());
    }

    @Test
    void truncate_keepCount1_writesFirst1Message() throws Exception {
        stubOwnedByAlice();
        when(valueOps.get(HISTORY_KEY)).thenReturn(HISTORY_4_JSON);

        historyService.truncateConversationHistory(USER_ID, CONV_ID, 1);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(HISTORY_KEY), jsonCaptor.capture(), any(Duration.class));
        List<?> written = new ObjectMapper().readValue(jsonCaptor.getValue(), List.class);
        assertEquals(1, written.size());
    }

    @Test
    void truncate_keepCount0_writesEmptyList() throws Exception {
        stubOwnedByAlice();
        when(valueOps.get(HISTORY_KEY)).thenReturn(HISTORY_4_JSON);

        historyService.truncateConversationHistory(USER_ID, CONV_ID, 0);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(HISTORY_KEY), jsonCaptor.capture(), any(Duration.class));
        List<?> written = new ObjectMapper().readValue(jsonCaptor.getValue(), List.class);
        assertTrue(written.isEmpty());
    }

    @Test
    void truncate_keepCountNegative_treatedAsZero() throws Exception {
        stubOwnedByAlice();
        when(valueOps.get(HISTORY_KEY)).thenReturn(HISTORY_4_JSON);

        historyService.truncateConversationHistory(USER_ID, CONV_ID, -5);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(HISTORY_KEY), jsonCaptor.capture(), any(Duration.class));
        List<?> written = new ObjectMapper().readValue(jsonCaptor.getValue(), List.class);
        assertTrue(written.isEmpty());
    }

    @Test
    void truncate_keepCountEqualToSize_noRedisWrite() {
        stubOwnedByAlice();
        when(valueOps.get(HISTORY_KEY)).thenReturn(HISTORY_4_JSON);

        historyService.truncateConversationHistory(USER_ID, CONV_ID, 4);

        verify(valueOps, never()).set(eq(HISTORY_KEY), anyString(), any(Duration.class));
    }

    @Test
    void truncate_keepCountExceedsSize_noRedisWrite() {
        stubOwnedByAlice();
        when(valueOps.get(HISTORY_KEY)).thenReturn(HISTORY_4_JSON);

        historyService.truncateConversationHistory(USER_ID, CONV_ID, 100);

        verify(valueOps, never()).set(eq(HISTORY_KEY), anyString(), any(Duration.class));
    }

    @Test
    void truncate_emptyHistory_keepCount0_noRedisWrite() {
        stubOwnedByAlice();
        when(valueOps.get(HISTORY_KEY)).thenReturn("[]");

        historyService.truncateConversationHistory(USER_ID, CONV_ID, 0);

        verify(valueOps, never()).set(eq(HISTORY_KEY), anyString(), any(Duration.class));
    }

    @Test
    void truncate_blankConversationId_throwsNotFoundException() {
        CustomException ex = assertThrows(CustomException.class,
                () -> historyService.truncateConversationHistory(USER_ID, "  ", 2));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void truncate_nullConversationId_throwsNotFoundException() {
        CustomException ex = assertThrows(CustomException.class,
                () -> historyService.truncateConversationHistory(USER_ID, null, 2));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void truncate_notOwnedByUser_throwsNotFoundException() {
        // META_KEY 未 stub → 归属校验默认失败
        CustomException ex = assertThrows(CustomException.class,
                () -> historyService.truncateConversationHistory(USER_ID, CONV_ID, 2));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // ---- isConversationOwnedByUser ----

    @Test
    void isConversationOwnedByUser_blankConversationId_returnsFalse() {
        assertFalse(historyService.isConversationOwnedByUser(USER_ID, "  "));
        assertFalse(historyService.isConversationOwnedByUser(USER_ID, null));
    }

    @Test
    void isConversationOwnedByUser_ownedViaMetaUserId_returnsTrue() {
        stubOwnedByAlice();
        assertTrue(historyService.isConversationOwnedByUser(USER_ID, CONV_ID));
    }

    @Test
    void isConversationOwnedByUser_ownedViaCurrentConversationId_returnsTrue() {
        when(valueOps.get(META_KEY)).thenReturn("{}");
        when(valueOps.get(CURRENT_KEY)).thenReturn(CONV_ID);

        assertTrue(historyService.isConversationOwnedByUser(USER_ID, CONV_ID));
    }

    @Test
    void isConversationOwnedByUser_ownedViaUserConversationIdsList_returnsTrue() {
        when(valueOps.get(META_KEY)).thenReturn("{}");
        when(valueOps.get(USER_IDS_KEY)).thenReturn("[\"" + CONV_ID + "\"]");

        assertTrue(historyService.isConversationOwnedByUser(USER_ID, CONV_ID));
    }

    @Test
    void isConversationOwnedByUser_notOwned_returnsFalse() {
        when(valueOps.get(META_KEY)).thenReturn("{\"userId\":\"bob\"}");

        assertFalse(historyService.isConversationOwnedByUser(USER_ID, CONV_ID));
    }

    // ---- resolveConversationId ----

    @Test
    void resolveConversationId_requestedOwned_attachesAndReturnsRequested() {
        stubOwnedByAlice();

        String result = historyService.resolveConversationId(USER_ID, CONV_ID);

        assertEquals(CONV_ID, result);
        verify(valueOps).set(eq(CURRENT_KEY), eq(CONV_ID), any(Duration.class));
    }

    @Test
    void resolveConversationId_requestedNotOwned_fallsBackToCurrent() {
        when(valueOps.get(META_KEY)).thenReturn("{}");
        when(valueOps.get(CURRENT_KEY)).thenReturn(CONV_ID);

        String result = historyService.resolveConversationId(USER_ID, "other-conv");

        assertEquals(CONV_ID, result);
    }

    @Test
    void resolveConversationId_noRequestedNoCurrent_createsNewConversation() {
        String result = historyService.resolveConversationId(USER_ID, null);

        assertNotNull(result);
        assertFalse(result.isBlank());
        verify(valueOps, atLeastOnce()).set(eq("conversation:" + result + ":meta"), anyString(), any(Duration.class));
    }

    // ---- getConversationMessages ----

    @Test
    void getConversationMessages_notOwned_returnsEmptyList() {
        List<Map<String, Object>> messages = historyService.getConversationMessages(USER_ID, CONV_ID);
        assertTrue(messages.isEmpty());
    }

    @Test
    void getConversationMessages_owned_returnsFormattedMessagesWithStatus() {
        stubOwnedByAlice();
        when(valueOps.get(HISTORY_KEY)).thenReturn(
                "[{\"role\":\"assistant\",\"content\":\"\",\"status\":\"error\",\"errorMessage\":\"boom\",\"timestamp\":\"2024-01-01 10:00:00\"}]");

        List<Map<String, Object>> messages = historyService.getConversationMessages(USER_ID, CONV_ID);

        assertEquals(1, messages.size());
        assertEquals("error", messages.get(0).get("status"));
        assertEquals("boom", messages.get(0).get("errorMessage"));
    }

    // ---- appendConversationTurn / appendConversationError ----

    @Test
    void appendConversationTurn_notOwned_throwsNotFound() {
        CustomException ex = assertThrows(CustomException.class,
                () -> historyService.appendConversationTurn(USER_ID, CONV_ID, "hi", "hello"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void appendConversationTurn_owned_appendsHistory() throws Exception {
        stubOwnedByAlice();

        historyService.appendConversationTurn(USER_ID, CONV_ID, "hello", "world");

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(HISTORY_KEY), jsonCaptor.capture(), any(Duration.class));
        List<Map<String, String>> written = new ObjectMapper().readValue(jsonCaptor.getValue(), List.class);
        assertEquals(2, written.size());
        assertEquals("user", written.get(0).get("role"));
        assertEquals("hello", written.get(0).get("content"));
        assertEquals("assistant", written.get(1).get("role"));
        assertEquals("world", written.get(1).get("content"));
    }

    @Test
    void appendConversationError_notOwned_throwsNotFound() {
        CustomException ex = assertThrows(CustomException.class,
                () -> historyService.appendConversationError(USER_ID, CONV_ID, "hi", "boom"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void appendConversationError_owned_appendsErrorHistory() throws Exception {
        stubOwnedByAlice();

        historyService.appendConversationError(USER_ID, CONV_ID, "hello", "boom");

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(HISTORY_KEY), jsonCaptor.capture(), any(Duration.class));
        List<Map<String, String>> written = new ObjectMapper().readValue(jsonCaptor.getValue(), List.class);
        assertEquals(2, written.size());
        assertEquals("error", written.get(1).get("status"));
        assertEquals("boom", written.get(1).get("errorMessage"));
    }

    // ---- createConversationSession ----

    @Test
    void createConversationSession_currentEmptyAndMetadataMatches_throwsBadRequest() {
        when(valueOps.get(CURRENT_KEY)).thenReturn(CONV_ID);
        when(valueOps.get(META_KEY)).thenReturn("{}");

        CustomException ex = assertThrows(CustomException.class,
                () -> historyService.createConversationSession(USER_ID, null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void createConversationSession_createsNewWhenNoCurrentConversation() {
        Map<String, Object> session = historyService.createConversationSession(USER_ID, null);

        assertNotNull(session.get("id"));
        assertEquals("新会话", session.get("title"));
    }

    // ---- deleteConversationSession ----

    @Test
    void deleteConversationSession_notOwned_throwsNotFound() {
        CustomException ex = assertThrows(CustomException.class,
                () -> historyService.deleteConversationSession(USER_ID, CONV_ID));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void deleteConversationSession_success_removesFromRedisAndPicksNext() {
        stubOwnedByAlice();
        when(valueOps.get(USER_IDS_KEY)).thenReturn("[\"conv-001\",\"conv-002\"]");
        when(valueOps.get(CURRENT_KEY)).thenReturn(CONV_ID);

        Map<String, Object> result = historyService.deleteConversationSession(USER_ID, CONV_ID);

        assertEquals(CONV_ID, result.get("deletedConversationId"));
        assertEquals("conv-002", result.get("currentConversationId"));
        assertEquals(1, result.get("remainingCount"));
        verify(redisTemplate).delete(HISTORY_KEY);
        verify(redisTemplate).delete(META_KEY);
        verify(valueOps).set(eq(CURRENT_KEY), eq("conv-002"), any(Duration.class));
    }

    // ---- updateConversationPinned ----

    @Test
    void updateConversationPinned_notOwned_throwsNotFound() {
        CustomException ex = assertThrows(CustomException.class,
                () -> historyService.updateConversationPinned(USER_ID, CONV_ID, true));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void updateConversationPinned_success_setsPinnedTrueWithTimestamp() {
        stubOwnedByAlice();

        Map<String, Object> result = historyService.updateConversationPinned(USER_ID, CONV_ID, true);

        assertEquals(CONV_ID, result.get("conversationId"));
        assertEquals(true, result.get("isPinned"));
        assertFalse(String.valueOf(result.get("pinnedAt")).isBlank());
        verify(valueOps).set(eq(META_KEY), contains("\"isPinned\":true"), any(Duration.class));
    }

    // ---- getConversationSessions ----

    @Test
    void getConversationSessions_currentConversationSortedFirst() {
        when(valueOps.get(USER_IDS_KEY)).thenReturn("[\"conv-001\",\"conv-002\"]");
        when(valueOps.get(CURRENT_KEY)).thenReturn("conv-002");
        when(valueOps.get(META_KEY)).thenReturn(
                "{\"userId\":\"alice\",\"title\":\"A\",\"updatedAt\":\"2024-01-01 10:00:00\",\"isPinned\":false}");
        when(valueOps.get("conversation:conv-002:meta")).thenReturn(
                "{\"userId\":\"alice\",\"title\":\"B\",\"updatedAt\":\"2024-01-01 09:00:00\",\"isPinned\":false}");

        List<Map<String, Object>> sessions = historyService.getConversationSessions(USER_ID, null, null, null);

        assertEquals(2, sessions.size());
        assertEquals("conv-002", sessions.get(0).get("id"));
    }

    // ---- ensureLegacyConversationIndex ----

    @Test
    void ensureLegacyConversationIndex_migratesFromLegacyUser() {
        when(valueOps.get("user:legacy1:conversation_ids")).thenReturn("[\"conv-legacy\"]");
        when(valueOps.get("conversation:conv-legacy:meta")).thenReturn("{}");

        historyService.ensureLegacyConversationIndex(USER_ID, List.of("legacy1"));

        verify(valueOps).set(eq(CURRENT_KEY), eq("conv-legacy"), any(Duration.class));
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(USER_IDS_KEY), jsonCaptor.capture(), any(Duration.class));
        assertTrue(jsonCaptor.getValue().contains("conv-legacy"));
    }
}

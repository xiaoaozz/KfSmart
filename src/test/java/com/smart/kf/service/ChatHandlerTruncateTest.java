package com.smart.kf.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.client.ModelClient;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatHandlerTruncateTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private HybridSearchService searchService;
    @Mock private ModelClient modelClient;
    @Mock private ElasticsearchService elasticsearchService;
    @Mock private ApiKeyConfigService apiKeyConfigService;
    @Mock private ConversationService conversationService;

    private ChatHandler chatHandler;

    private static final String USER_ID = "alice";
    private static final String CONV_ID = "conv-001";
    private static final String HISTORY_KEY = "conversation:" + CONV_ID;

    // 4-entry history: 2 turns (user + assistant × 2)
    private static final String HISTORY_4_JSON =
            "[{\"role\":\"user\",\"content\":\"q1\",\"timestamp\":\"2024-01-01 10:00:00\"},"
            + "{\"role\":\"assistant\",\"content\":\"a1\",\"timestamp\":\"2024-01-01 10:00:01\"},"
            + "{\"role\":\"user\",\"content\":\"q2\",\"timestamp\":\"2024-01-01 10:01:00\"},"
            + "{\"role\":\"assistant\",\"content\":\"a2\",\"timestamp\":\"2024-01-01 10:01:01\"}]";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        chatHandler = spy(new ChatHandler(
                redisTemplate, searchService, modelClient,
                elasticsearchService, apiKeyConfigService, conversationService));
        // Bypass ownership check in all tests unless the test overrides it
        doReturn(true).when(chatHandler).isConversationOwnedByUser(USER_ID, CONV_ID);
    }

    // ---- normal truncation ----

    @Test
    void truncate_keepCount2_writesFirst2Messages() throws Exception {
        when(valueOps.get(HISTORY_KEY)).thenReturn(HISTORY_4_JSON);

        chatHandler.truncateConversationHistory(USER_ID, CONV_ID, 2);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(HISTORY_KEY), jsonCaptor.capture(), any(Duration.class));
        List<?> written = new ObjectMapper().readValue(jsonCaptor.getValue(), List.class);
        assertEquals(2, written.size());
    }

    @Test
    void truncate_keepCount1_writesFirst1Message() throws Exception {
        when(valueOps.get(HISTORY_KEY)).thenReturn(HISTORY_4_JSON);

        chatHandler.truncateConversationHistory(USER_ID, CONV_ID, 1);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(HISTORY_KEY), jsonCaptor.capture(), any(Duration.class));
        List<?> written = new ObjectMapper().readValue(jsonCaptor.getValue(), List.class);
        assertEquals(1, written.size());
    }

    // ---- keepCount = 0 ----

    @Test
    void truncate_keepCount0_writesEmptyList() throws Exception {
        when(valueOps.get(HISTORY_KEY)).thenReturn(HISTORY_4_JSON);

        chatHandler.truncateConversationHistory(USER_ID, CONV_ID, 0);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(HISTORY_KEY), jsonCaptor.capture(), any(Duration.class));
        List<?> written = new ObjectMapper().readValue(jsonCaptor.getValue(), List.class);
        assertTrue(written.isEmpty());
    }

    // ---- negative keepCount treated as 0 ----

    @Test
    void truncate_keepCountNegative_treatedAsZero() throws Exception {
        when(valueOps.get(HISTORY_KEY)).thenReturn(HISTORY_4_JSON);

        chatHandler.truncateConversationHistory(USER_ID, CONV_ID, -5);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(HISTORY_KEY), jsonCaptor.capture(), any(Duration.class));
        List<?> written = new ObjectMapper().readValue(jsonCaptor.getValue(), List.class);
        assertTrue(written.isEmpty());
    }

    // ---- no-op when keepCount >= size ----

    @Test
    void truncate_keepCountEqualToSize_noRedisWrite() {
        when(valueOps.get(HISTORY_KEY)).thenReturn(HISTORY_4_JSON);

        chatHandler.truncateConversationHistory(USER_ID, CONV_ID, 4);

        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void truncate_keepCountExceedsSize_noRedisWrite() {
        when(valueOps.get(HISTORY_KEY)).thenReturn(HISTORY_4_JSON);

        chatHandler.truncateConversationHistory(USER_ID, CONV_ID, 100);

        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    // ---- empty history ----

    @Test
    void truncate_emptyHistory_keepCount0_noRedisWrite() {
        when(valueOps.get(HISTORY_KEY)).thenReturn("[]");

        chatHandler.truncateConversationHistory(USER_ID, CONV_ID, 0);

        // safeKeep=min(0,0)=0 == size=0 → no-op
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    // ---- guard: blank conversationId ----

    @Test
    void truncate_blankConversationId_throwsNotFoundException() {
        CustomException ex = assertThrows(CustomException.class,
                () -> chatHandler.truncateConversationHistory(USER_ID, "  ", 2));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void truncate_nullConversationId_throwsNotFoundException() {
        CustomException ex = assertThrows(CustomException.class,
                () -> chatHandler.truncateConversationHistory(USER_ID, null, 2));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // ---- guard: conversation not owned by user ----

    @Test
    void truncate_notOwnedByUser_throwsNotFoundException() {
        doReturn(false).when(chatHandler).isConversationOwnedByUser(USER_ID, CONV_ID);

        CustomException ex = assertThrows(CustomException.class,
                () -> chatHandler.truncateConversationHistory(USER_ID, CONV_ID, 2));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}

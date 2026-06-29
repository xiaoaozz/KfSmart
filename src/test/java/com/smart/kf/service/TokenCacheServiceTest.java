package com.smart.kf.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenCacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    @Mock
    private SetOperations<String, Object> setOps;

    @InjectMocks
    private TokenCacheService tokenCacheService;

    private static final long FUTURE_EXPIRE = System.currentTimeMillis() + 3600_000L;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
    }

    // cacheToken

    @Test
    void cacheToken_storesTokenInfoAndAddsToUserSet() {
        tokenCacheService.cacheToken("tok_1", "user_1", "alice", FUTURE_EXPIRE);

        verify(valueOps).set(eq("jwt:valid:tok_1"), any(Map.class), anyLong(), eq(TimeUnit.SECONDS));
        verify(setOps).add(eq("jwt:user:user_1:tokens"), eq("tok_1"));
    }

    @Test
    void cacheToken_pastExpireTime_stillCallsRedis() {
        long pastExpire = System.currentTimeMillis() - 1000L;
        tokenCacheService.cacheToken("tok_expired", "user_1", "alice", pastExpire);
        // even with a past expiry the cache call is attempted (negative TTL)
        verify(redisTemplate, atLeastOnce()).opsForValue();
    }

    // isTokenValid

    @Test
    void isTokenValid_blacklisted_returnsFalse() {
        when(redisTemplate.hasKey("jwt:blacklist:tok_1")).thenReturn(true);

        assertFalse(tokenCacheService.isTokenValid("tok_1"));
        verify(redisTemplate, never()).hasKey("jwt:valid:tok_1");
    }

    @Test
    void isTokenValid_notInCache_returnsFalse() {
        when(redisTemplate.hasKey("jwt:blacklist:tok_1")).thenReturn(false);
        when(redisTemplate.hasKey("jwt:valid:tok_1")).thenReturn(false);

        assertFalse(tokenCacheService.isTokenValid("tok_1"));
    }

    @Test
    void isTokenValid_inCacheNotBlacklisted_returnsTrue() {
        when(redisTemplate.hasKey("jwt:blacklist:tok_1")).thenReturn(false);
        when(redisTemplate.hasKey("jwt:valid:tok_1")).thenReturn(true);

        assertTrue(tokenCacheService.isTokenValid("tok_1"));
    }

    // blacklistToken

    @Test
    void blacklistToken_futureExpiry_storesInRedis() {
        tokenCacheService.blacklistToken("tok_1", FUTURE_EXPIRE);

        verify(valueOps).set(eq("jwt:blacklist:tok_1"), anyLong(), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void blacklistToken_pastExpiry_doesNotStore() {
        long pastExpire = System.currentTimeMillis() - 5000L;
        tokenCacheService.blacklistToken("tok_old", pastExpire);

        verify(valueOps, never()).set(eq("jwt:blacklist:tok_old"), any(), anyLong(), any());
    }

    // removeToken

    @Test
    void removeToken_deletesKeyAndRemovesFromUserSet() {
        tokenCacheService.removeToken("tok_1", "user_1");

        verify(redisTemplate).delete("jwt:valid:tok_1");
        verify(setOps).remove("jwt:user:user_1:tokens", "tok_1");
    }

    @Test
    void removeToken_nullUserId_deletesKeyOnly() {
        tokenCacheService.removeToken("tok_1", null);

        verify(redisTemplate).delete("jwt:valid:tok_1");
        verify(setOps, never()).remove(anyString(), any());
    }

    // removeAllUserTokens

    @Test
    void removeAllUserTokens_clearsAllUserTokens() {
        when(setOps.members("jwt:user:user_1:tokens")).thenReturn(Set.of("tok_a", "tok_b"));

        tokenCacheService.removeAllUserTokens("user_1");

        verify(redisTemplate).delete("jwt:valid:tok_a");
        verify(redisTemplate).delete("jwt:valid:tok_b");
        verify(redisTemplate).delete("jwt:user:user_1:tokens");
    }

    @Test
    void removeAllUserTokens_noTokens_justDeletesSetKey() {
        when(setOps.members("jwt:user:user_2:tokens")).thenReturn(Set.of());

        tokenCacheService.removeAllUserTokens("user_2");

        verify(redisTemplate).delete("jwt:user:user_2:tokens");
    }

    // isRefreshTokenValid

    @Test
    void isRefreshTokenValid_exists_returnsTrue() {
        when(redisTemplate.hasKey("jwt:refresh:rtok_1")).thenReturn(true);

        assertTrue(tokenCacheService.isRefreshTokenValid("rtok_1"));
    }

    @Test
    void isRefreshTokenValid_notExists_returnsFalse() {
        when(redisTemplate.hasKey("jwt:refresh:rtok_1")).thenReturn(false);

        assertFalse(tokenCacheService.isRefreshTokenValid("rtok_1"));
    }

    // getUserActiveTokenCount

    @Test
    void getUserActiveTokenCount_returnsCount() {
        when(setOps.size("jwt:user:user_1:tokens")).thenReturn(3L);

        assertEquals(3L, tokenCacheService.getUserActiveTokenCount("user_1"));
    }

    @Test
    void getUserActiveTokenCount_nullFromRedis_returnsZero() {
        when(setOps.size("jwt:user:user_1:tokens")).thenReturn(null);

        assertEquals(0L, tokenCacheService.getUserActiveTokenCount("user_1"));
    }
}

package com.fhwang.shortlinkops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class LinkCacheServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private LinkCacheService linkCacheService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        linkCacheService = new LinkCacheService(redisTemplate);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getOriginalUrlReturnsCachedValue() {
        when(valueOperations.get("shortlink:abc123")).thenReturn("https://example.com");

        Optional<String> originalUrl = linkCacheService.getOriginalUrl("abc123");

        assertThat(originalUrl).contains("https://example.com");
    }

    @Test
    void getOriginalUrlFallsBackToEmptyWhenRedisFails() {
        when(valueOperations.get("shortlink:abc123"))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));

        Optional<String> originalUrl = linkCacheService.getOriginalUrl("abc123");

        assertThat(originalUrl).isEmpty();
    }

    @Test
    void cacheOriginalUrlStoresValueWithDefaultTtl() {
        linkCacheService.cacheOriginalUrl("abc123", "https://example.com", null);

        verify(valueOperations).set("shortlink:abc123", "https://example.com", Duration.ofHours(24));
    }

    @Test
    void cacheOriginalUrlStoresValueWithExpiresAtTtl() {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

        linkCacheService.cacheOriginalUrl("abc123", "https://example.com", expiresAt);

        verify(valueOperations).set(any(), any(), any(Duration.class));
    }

    @Test
    void cacheOriginalUrlSkipsExpiredTtl() {
        LocalDateTime expiresAt = LocalDateTime.now().minusSeconds(1);

        linkCacheService.cacheOriginalUrl("abc123", "https://example.com", expiresAt);

        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }
}

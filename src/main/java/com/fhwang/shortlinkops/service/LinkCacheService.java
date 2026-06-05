package com.fhwang.shortlinkops.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinkCacheService {

    private static final String KEY_PREFIX = "shortlink:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public Optional<String> getOriginalUrl(String shortCode) {
        String key = buildKey(shortCode);

        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key));
        } catch (RuntimeException ex) {
            log.warn("Failed to read short link cache. shortCode={}", shortCode, ex);
            return Optional.empty();
        }
    }

    public void cacheOriginalUrl(String shortCode, String originalUrl, LocalDateTime expiresAt) {
        Duration ttl = calculateTtl(expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }

        String key = buildKey(shortCode);

        try {
            redisTemplate.opsForValue().set(key, originalUrl, ttl);
        } catch (RuntimeException ex) {
            log.warn("Failed to write short link cache. shortCode={}", shortCode, ex);
        }
    }

    private Duration calculateTtl(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            return DEFAULT_TTL;
        }

        return Duration.between(LocalDateTime.now(), expiresAt);
    }

    private String buildKey(String shortCode) {
        return KEY_PREFIX + shortCode;
    }
}

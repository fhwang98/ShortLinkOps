package com.fhwang.shortlinkops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fhwang.shortlinkops.domain.Link;
import com.fhwang.shortlinkops.dto.CreateLinkRequest;
import com.fhwang.shortlinkops.dto.CreateLinkResponse;
import com.fhwang.shortlinkops.dto.LinkResponse;
import com.fhwang.shortlinkops.exception.ExpiredLinkException;
import com.fhwang.shortlinkops.exception.LinkNotFoundException;
import com.fhwang.shortlinkops.mapper.LinkMapper;
import com.fhwang.shortlinkops.util.ShortCodeGenerator;

class LinkServiceTest {

    private LinkMapper linkMapper;
    private ShortCodeGenerator shortCodeGenerator;
    private LinkCacheService linkCacheService;
    private LinkService linkService;

    @BeforeEach
    void setUp() {
        linkMapper = org.mockito.Mockito.mock(LinkMapper.class);
        shortCodeGenerator = org.mockito.Mockito.mock(ShortCodeGenerator.class);
        linkCacheService = org.mockito.Mockito.mock(LinkCacheService.class);
        linkService = new LinkService(linkMapper, shortCodeGenerator, linkCacheService);
        ReflectionTestUtils.setField(linkService, "baseUrl", "http://short.test");
    }

    @Test
    void createLinkCreatesShortCodeAndReturnsResponse() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 31, 12, 0);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 12, 31, 23, 59, 59);
        Link savedLink = Link.builder()
                .id(1L)
                .originalUrl("https://example.com/articles/1")
                .shortCode("abc123")
                .clickCount(0L)
                .expiresAt(expiresAt)
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(shortCodeGenerator.generate()).thenReturn("abc123");
        when(linkMapper.existsByShortCode("abc123")).thenReturn(0);
        when(linkMapper.insert(any(Link.class))).thenReturn(1);
        when(linkMapper.findByShortCode("abc123")).thenReturn(Optional.of(savedLink));

        CreateLinkResponse response = linkService.createLink(CreateLinkRequest.builder()
                .originalUrl("https://example.com/articles/1")
                .expiresAt(expiresAt)
                .build());

        assertThat(response.getShortCode()).isEqualTo("abc123");
        assertThat(response.getOriginalUrl()).isEqualTo("https://example.com/articles/1");
        assertThat(response.getShortUrl()).isEqualTo("http://short.test/s/abc123");
        assertThat(response.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(response.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void getLinkThrowsWhenShortCodeDoesNotExist() {
        when(linkMapper.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> linkService.getLink("missing"))
                .isInstanceOf(LinkNotFoundException.class);
    }

    @Test
    void getLinkReturnsDetailResponse() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 31, 12, 0);
        Link link = Link.builder()
                .id(1L)
                .originalUrl("https://example.com")
                .shortCode("abc123")
                .clickCount(7L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(linkMapper.findByShortCode("abc123")).thenReturn(Optional.of(link));

        LinkResponse response = linkService.getLink("abc123");

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getOriginalUrl()).isEqualTo("https://example.com");
        assertThat(response.getShortUrl()).isEqualTo("http://short.test/s/abc123");
        assertThat(response.getClickCount()).isEqualTo(7L);
    }

    @Test
    void resolveOriginalUrlReturnsCachedOriginalUrl() {
        when(linkCacheService.getOriginalUrl("abc123")).thenReturn(Optional.of("https://example.com"));

        String originalUrl = linkService.resolveOriginalUrl("abc123");

        assertThat(originalUrl).isEqualTo("https://example.com");
        verify(linkMapper, never()).findByShortCode(anyString());
        verify(linkMapper).incrementClickCount("abc123");
        verify(linkCacheService, never()).cacheOriginalUrl(anyString(), anyString(), any());
    }

    @Test
    void resolveOriginalUrlIncrementsClickCountAndCachesOnCacheMiss() {
        Link link = Link.builder()
                .originalUrl("https://example.com")
                .shortCode("abc123")
                .clickCount(0L)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(linkCacheService.getOriginalUrl("abc123")).thenReturn(Optional.empty());
        when(linkMapper.findByShortCode("abc123")).thenReturn(Optional.of(link));

        String originalUrl = linkService.resolveOriginalUrl("abc123");

        assertThat(originalUrl).isEqualTo("https://example.com");
        verify(linkCacheService).cacheOriginalUrl("abc123", "https://example.com", link.getExpiresAt());
        verify(linkMapper).incrementClickCount("abc123");
    }

    @Test
    void resolveOriginalUrlThrowsWhenLinkExpired() {
        Link link = Link.builder()
                .originalUrl("https://example.com")
                .shortCode("abc123")
                .clickCount(0L)
                .expiresAt(LocalDateTime.now().minusSeconds(1))
                .build();

        when(linkCacheService.getOriginalUrl("abc123")).thenReturn(Optional.empty());
        when(linkMapper.findByShortCode("abc123")).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> linkService.resolveOriginalUrl("abc123"))
                .isInstanceOf(ExpiredLinkException.class);
        verify(linkCacheService, never()).cacheOriginalUrl(anyString(), anyString(), any());
        verify(linkMapper, never()).incrementClickCount("abc123");
    }
}

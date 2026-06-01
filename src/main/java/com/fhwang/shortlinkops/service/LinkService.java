package com.fhwang.shortlinkops.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.fhwang.shortlinkops.domain.Link;
import com.fhwang.shortlinkops.dto.CreateLinkRequest;
import com.fhwang.shortlinkops.dto.CreateLinkResponse;
import com.fhwang.shortlinkops.dto.LinkResponse;
import com.fhwang.shortlinkops.dto.LinkStatsResponse;
import com.fhwang.shortlinkops.exception.ExpiredLinkException;
import com.fhwang.shortlinkops.exception.LinkNotFoundException;
import com.fhwang.shortlinkops.mapper.LinkMapper;
import com.fhwang.shortlinkops.util.ShortCodeGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LinkService {

    private static final int MAX_SHORT_CODE_RETRY_COUNT = 5;

    private final LinkMapper linkMapper;
    private final ShortCodeGenerator shortCodeGenerator;
    private final LinkCacheService linkCacheService;

    @Value("${shortlinkops.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional
    public CreateLinkResponse createLink(CreateLinkRequest request) {
        Link savedLink = createWithUniqueShortCode(request);

        return CreateLinkResponse.builder()
                .shortCode(savedLink.getShortCode())
                .originalUrl(savedLink.getOriginalUrl())
                .shortUrl(buildShortUrl(savedLink.getShortCode()))
                .expiresAt(savedLink.getExpiresAt())
                .createdAt(savedLink.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public LinkResponse getLink(String shortCode) {
        Link link = findLink(shortCode);

        return LinkResponse.builder()
                .id(link.getId())
                .originalUrl(link.getOriginalUrl())
                .shortCode(link.getShortCode())
                .shortUrl(buildShortUrl(link.getShortCode()))
                .clickCount(link.getClickCount())
                .expiresAt(link.getExpiresAt())
                .createdAt(link.getCreatedAt())
                .updatedAt(link.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public LinkStatsResponse getStats(String shortCode) {
        Link link = findLink(shortCode);

        return LinkStatsResponse.builder()
                .shortCode(link.getShortCode())
                .clickCount(link.getClickCount())
                .build();
    }

    @Transactional
    public String resolveOriginalUrl(String shortCode) {
        return linkCacheService.getOriginalUrl(shortCode)
                .map(originalUrl -> {
                    linkMapper.incrementClickCount(shortCode);
                    return originalUrl;
                })
                .orElseGet(() -> resolveOriginalUrlFromDatabase(shortCode));
    }

    private String resolveOriginalUrlFromDatabase(String shortCode) {
        Link link = findLink(shortCode);

        if (link.isExpired()) {
            throw new ExpiredLinkException();
        }

        linkCacheService.cacheOriginalUrl(shortCode, link.getOriginalUrl(), link.getExpiresAt());
        linkMapper.incrementClickCount(shortCode);
        return link.getOriginalUrl();
    }

    private Link createWithUniqueShortCode(CreateLinkRequest request) {
        for (int retryCount = 0; retryCount < MAX_SHORT_CODE_RETRY_COUNT; retryCount++) {
            String shortCode = shortCodeGenerator.generate();

            if (linkMapper.existsByShortCode(shortCode) > 0) {
                continue;
            }

            Link link = Link.builder()
                    .originalUrl(request.getOriginalUrl())
                    .shortCode(shortCode)
                    .expiresAt(request.getExpiresAt())
                    .build();

            try {
                linkMapper.insert(link);
                return linkMapper.findByShortCode(shortCode).orElse(link);
            } catch (DuplicateKeyException ex) {
                // A concurrent insert may win the same shortCode after the existence check.
            }
        }

        throw new IllegalStateException("단축 코드 생성에 실패했습니다.");
    }

    private Link findLink(String shortCode) {
        return linkMapper.findByShortCode(shortCode)
                .orElseThrow(LinkNotFoundException::new);
    }

    private String buildShortUrl(String shortCode) {
        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;

        return UriComponentsBuilder.fromUriString(normalizedBaseUrl)
                .path("/s/{shortCode}")
                .buildAndExpand(shortCode)
                .toUriString();
    }
}

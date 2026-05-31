package com.fhwang.shortlinkops.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class CreateLinkResponse {

    private String shortCode;
    private String originalUrl;
    private String shortUrl;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}

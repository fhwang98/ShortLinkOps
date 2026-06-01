package com.fhwang.shortlinkops.dto;

import java.time.LocalDateTime;

import org.hibernate.validator.constraints.URL;
import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateLinkRequest {

    @NotBlank(message = "원본 URL은 필수입니다.")
    @URL(message = "올바른 URL 형식이 아닙니다.")
    private String originalUrl;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime expiresAt;
}

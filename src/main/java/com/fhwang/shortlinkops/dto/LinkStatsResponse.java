package com.fhwang.shortlinkops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class LinkStatsResponse {

    private String shortCode;
    private Long clickCount;
}

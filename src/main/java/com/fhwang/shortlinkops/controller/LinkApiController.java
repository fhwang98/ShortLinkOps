package com.fhwang.shortlinkops.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fhwang.shortlinkops.dto.CreateLinkRequest;
import com.fhwang.shortlinkops.dto.CreateLinkResponse;
import com.fhwang.shortlinkops.dto.LinkResponse;
import com.fhwang.shortlinkops.dto.LinkStatsResponse;
import com.fhwang.shortlinkops.service.LinkService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
public class LinkApiController {

    private final LinkService linkService;

    @PostMapping
    public ResponseEntity<CreateLinkResponse> createLink(@Valid @RequestBody CreateLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(linkService.createLink(request));
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<LinkResponse> getLink(@PathVariable String shortCode) {
        return ResponseEntity.ok(linkService.getLink(shortCode));
    }

    @GetMapping("/{shortCode}/stats")
    public ResponseEntity<LinkStatsResponse> getStats(@PathVariable String shortCode) {
        return ResponseEntity.ok(linkService.getStats(shortCode));
    }
}

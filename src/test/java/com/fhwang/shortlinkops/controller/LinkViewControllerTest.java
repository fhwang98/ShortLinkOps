package com.fhwang.shortlinkops.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fhwang.shortlinkops.dto.CreateLinkRequest;
import com.fhwang.shortlinkops.dto.CreateLinkResponse;
import com.fhwang.shortlinkops.service.LinkService;

@SpringBootTest
@AutoConfigureMockMvc
class LinkViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LinkService linkService;

    @Test
    void indexReturnsMainPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("request"))
                .andExpect(content().string(containsString("단축 URL 생성")));
    }

    @Test
    void createLinkFromFormReturnsResultPage() throws Exception {
        mockMvc.perform(post("/links")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("originalUrl", "https://example.com/articles/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("result"))
                .andExpect(model().attributeExists("link"))
                .andExpect(content().string(containsString("단축 URL 생성 완료")));
    }

    @Test
    void createLinkFromFormValidationFailureReturnsIndexPage() throws Exception {
        mockMvc.perform(post("/links")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("originalUrl", "invalid-url"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeHasFieldErrors("request", "originalUrl"));
    }

    @Test
    void detailReturnsLinkDetailPage() throws Exception {
        CreateLinkResponse link = createLink("https://example.com/detail", null);

        mockMvc.perform(get("/links/{shortCode}", link.getShortCode()))
                .andExpect(status().isOk())
                .andExpect(view().name("detail"))
                .andExpect(model().attributeExists("link"))
                .andExpect(content().string(containsString("단축 URL 상세")));
    }

    @Test
    void missingLinkViewRequestReturnsErrorPage() throws Exception {
        mockMvc.perform(get("/links/not-found-code").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(content().string(containsString("존재하지 않는 단축 URL입니다.")));
    }

    @Test
    void expiredRedirectViewRequestReturnsErrorPage() throws Exception {
        CreateLinkResponse link = createLink("https://example.com/expired", LocalDateTime.now().minusDays(1));

        mockMvc.perform(get("/s/{shortCode}", link.getShortCode()).accept(MediaType.TEXT_HTML))
                .andExpect(status().isGone())
                .andExpect(view().name("error/410"))
                .andExpect(content().string(containsString("만료된 단축 URL입니다.")));
    }

    @Test
    void apiErrorStillReturnsJson() throws Exception {
        mockMvc.perform(get("/api/links/not-found-code").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"));
    }

    private CreateLinkResponse createLink(String originalUrl, LocalDateTime expiresAt) {
        return linkService.createLink(CreateLinkRequest.builder()
                .originalUrl(originalUrl)
                .expiresAt(expiresAt)
                .build());
    }
}

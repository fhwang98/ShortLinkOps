package com.fhwang.shortlinkops.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.fhwang.shortlinkops.dto.CreateLinkRequest;
import com.fhwang.shortlinkops.dto.CreateLinkResponse;
import com.fhwang.shortlinkops.dto.LinkResponse;
import com.fhwang.shortlinkops.service.LinkService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class LinkViewController {

    private final LinkService linkService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("request", new CreateLinkRequest());
        return "index";
    }

    @PostMapping("/links")
    public String createLink(
            @Valid @ModelAttribute("request") CreateLinkRequest request,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return "index";
        }

        CreateLinkResponse response = linkService.createLink(request);
        model.addAttribute("link", response);
        return "result";
    }

    @GetMapping("/links/{shortCode}")
    public String detail(@PathVariable String shortCode, Model model) {
        LinkResponse response = linkService.getLink(shortCode);
        model.addAttribute("link", response);
        return "detail";
    }
}

package com.example.chillgram.domain.ai.controller;

import com.example.chillgram.common.security.AuthPrincipal;
import com.example.chillgram.domain.advertising.service.AdService;
import com.example.chillgram.domain.ai.dto.AdGuideRequest;
import com.example.chillgram.domain.ai.dto.AdGuideResponse;
import com.example.chillgram.domain.ai.dto.CopyVariationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "AI AdCopy", description = "AI 광고 카피 및 가이드라인 생성")
public class AdCopyController {

    private final AdService adService;

    @Operation(summary = "1단계: 비주얼 가이드라인 생성")
    @PostMapping("/projects/{projectId}/ad-guides")
    public Mono<AdGuideResponse> generateVisualGuides(
            @PathVariable Long projectId,
            @RequestBody AdGuideRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {

        return adService.generateAdGuides(projectId, request, principal.companyId());
    }

    @Operation(summary = "2단계: 광고 카피 베리에이션 생성")
    @PostMapping("/projects/{projectId}/copies")
    public Mono<List<String>> generateCopyVariations(
            @PathVariable Long projectId,
            @RequestBody CopyVariationRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {

        return adService.generateCopyVariations(projectId, request, principal.companyId());
    }
}

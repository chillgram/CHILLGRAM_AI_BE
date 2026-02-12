package com.example.chillgram.domain.advertising.dto;

import com.example.chillgram.domain.ai.dto.FinalCopyResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record AdCreateRequest(
        String productName,
        String projectType, // AD, GUIDE etc.
        String projectTitle,
        String adGoal,
        String requestText,
        List<String> selectedKeywords,
        Integer adFocus,
        Integer adMessageTarget,

        String baseDate,
        String bannerSize,
        String platform,

        String selectedGuideId,
        JsonNode selectedGuide,

        String selectedCopyId,
        FinalCopyResponse selectedCopy,

        List<String> selectedTypes) {
    public AdCreateRequest {
        selectedKeywords = selectedKeywords == null ? List.of() : List.copyOf(selectedKeywords);
        selectedTypes = selectedTypes == null ? List.of() : List.copyOf(selectedTypes);
    }
}

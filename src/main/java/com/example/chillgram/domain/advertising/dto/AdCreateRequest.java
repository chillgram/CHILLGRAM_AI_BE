package com.example.chillgram.domain.advertising.dto;

import com.example.chillgram.domain.ai.dto.FinalCopyResponse;
import java.util.List;

public record AdCreateRequest(
        String productName,
        String projectTitle,
        String adGoal,
        String requestText,
        List<String> selectedKeywords,
        Integer adFocus,

        String selectedGuideId,
        Object selectedGuide, // 지금은 통째로 받기(나중에 Guide DTO로 고정)

        String selectedCopyId,
        FinalCopyResponse selectedCopy, // Step3 선택 문구(최종 카피)

        List<String> selectedTypes // Step4 선택 타입(복수)
) {
    public AdCreateRequest {
        selectedKeywords = selectedKeywords == null ? List.of() : List.copyOf(selectedKeywords);
        selectedTypes = selectedTypes == null ? List.of() : List.copyOf(selectedTypes);
    }
}
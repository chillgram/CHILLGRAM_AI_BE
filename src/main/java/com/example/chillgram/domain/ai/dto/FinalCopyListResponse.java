package com.example.chillgram.domain.ai.dto;

import java.util.List;

public record FinalCopyListResponse(
        String recommendedConcept,          // 추천 컨셉(옵션)
        List<FinalCopyItem> copies
) {
    public record FinalCopyItem(
            String id,                      // 프론트 선택키 (필수)
            FinalCopyResponse result         // 결과 본문
    ) {}

    public static FinalCopyListResponse of(String recommendedConcept, List<FinalCopyItem> copies) {
        return new FinalCopyListResponse(recommendedConcept, copies);
    }
}
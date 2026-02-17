package com.example.chillgram.domain.advertising.dto;

import java.time.LocalDate;
import java.util.List;

public record AdGuidesRequest(
        LocalDate baseDate,
        String title,
        String adGoal,
        String requestText,
        List<String> selectedKeywords,
        String adFocus,
        Integer adMessageFocus,    // 0: 트렌드 중심 ~ 4: 제품 특징(리뷰) 중심
        Integer adMessageTarget,   // 0: 인지, 1: 공감, 2: 보상, 3: 참여, 4: 행동
        String reviewText          // 제품 리뷰 요약 (3줄)
) {}
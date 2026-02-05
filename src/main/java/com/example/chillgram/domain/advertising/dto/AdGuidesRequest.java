package com.example.chillgram.domain.advertising.dto;

import java.time.LocalDate;
import java.util.List;

public record AdGuidesRequest(
        LocalDate baseDate,
        String title,
        String adGoal,
        String requestText,
        List<String> selectedKeywords,
        String adFocus
) {}
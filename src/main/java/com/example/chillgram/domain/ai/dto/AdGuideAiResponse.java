package com.example.chillgram.domain.ai.dto;

import java.util.List;

public record AdGuideAiResponse(
        List<Section> sections
) {
    public record Section(String section, String content) {}
}
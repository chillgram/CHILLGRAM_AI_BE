package com.example.chillgram.domain.ai.dto;

import java.util.List;

public record AdGuideAiResponse(
        List<Guideline> guidelines
) {
    public record Guideline(int id, List<Section> sections) {}
    public record Section(String section, String content) {}
}

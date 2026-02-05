package com.example.chillgram.domain.ai.dto;

import java.util.List;

public record AdCopiesResponse(
        String recommendedCopyId,
        List<AdCopyItem> copies
) {
    public record AdCopyItem(
            String id,
            String title,
            String body
    ) {}
}
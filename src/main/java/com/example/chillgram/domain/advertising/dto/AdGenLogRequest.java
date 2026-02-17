package com.example.chillgram.domain.advertising.dto;

import java.util.Map;

public record AdGenLogRequest(
        Long productId,
        Map<String, Object> finalCopy,
        Map<String, Object> guideline,
        String selectionReason) {
}

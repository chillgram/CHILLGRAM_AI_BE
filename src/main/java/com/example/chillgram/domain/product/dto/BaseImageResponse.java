package com.example.chillgram.domain.product.dto;

import java.time.LocalDateTime;

public record BaseImageResponse(
        String url,
        String jobType, // SNS, BANNER, VIDEO, BASIC
        Long projectId,
        LocalDateTime completedAt) {
}

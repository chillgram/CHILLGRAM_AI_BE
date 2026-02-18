package com.example.chillgram.domain.advertising.dto;

import java.util.List;

public record AdCreateResponse(
        Long projectId,
        List<Long> contentIds,
        String category) {}
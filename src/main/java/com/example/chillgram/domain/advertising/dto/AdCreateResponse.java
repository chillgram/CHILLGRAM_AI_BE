package com.example.chillgram.domain.advertising.dto;

import java.util.List;

public record AdCreateResponse(
        long projectId,
        List<Long> contentIds
) {}
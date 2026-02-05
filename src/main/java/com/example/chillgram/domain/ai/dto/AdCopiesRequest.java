package com.example.chillgram.domain.ai.dto;

public record AdCopiesRequest(
        Long productId,
        String keyword,
        String selectedConcept,
        String selectedDescription,
        String tone
) {}
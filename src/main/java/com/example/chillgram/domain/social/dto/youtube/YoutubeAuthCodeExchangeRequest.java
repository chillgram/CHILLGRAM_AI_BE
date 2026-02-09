package com.example.chillgram.domain.social.dto.youtube;

import jakarta.validation.constraints.NotBlank;

public record YoutubeAuthCodeExchangeRequest(
        @NotBlank String code,
        @NotBlank String state
) {}
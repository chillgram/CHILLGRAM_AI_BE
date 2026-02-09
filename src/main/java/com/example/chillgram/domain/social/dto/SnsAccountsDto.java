package com.example.chillgram.domain.social.dto;

public record SnsAccountsDto(
        SnsAccountDto youtube,
        SnsAccountDto instagram
) {}
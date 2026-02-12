package com.example.chillgram.domain.advertising.dto.jobs;

import jakarta.validation.constraints.Size;

public record JobResultRequest(
        boolean success,
        String outputUri,               // success=true일 때
        @Size(max = 50) String errorCode, // success=false일 때
        String errorMessage
) {}
package com.example.chillgram.common.exception;


import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String method,
        String traceId,
        Map<String, Object> details
) {
    public static ApiErrorResponse of(
            int status,
            String code,
            String message,
            String path,
            String method,
            String traceId,
            Map<String, Object> details
    ) {
        return new ApiErrorResponse(Instant.now(), status, code, message, path, method, traceId, details);
    }
}
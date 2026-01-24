package com.example.chillgram.common.logging.model;

/**
 * 요청 1건에 대한 공통 메타데이터(운영/성능 추적용).
 * - traceId: 요청 상관관계 ID
 * - method/path: 요청 식별자
 * - status: 응답 HTTP 상태코드
 * - latencyMs: 처리 지연시간(ms)
 */
public record RequestMeta(
        String traceId,
        String method,
        String path,
        int status,
        long latencyMs
) {}
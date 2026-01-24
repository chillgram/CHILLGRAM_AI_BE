package com.example.chillgram.common.exception;

import reactor.core.publisher.Mono;

import java.util.Map;

public final class DomainExceptionFactory {

    private DomainExceptionFactory() {}

    // throw 용 (예외 객체 생성)
    public static BusinessException notFoundEx(String message, Map<String, Object> details) {
        return new BusinessException(ErrorCode.NOT_FOUND, message, details);
    }

    public static BusinessException conflictEx(String message, Map<String, Object> details) {
        return new BusinessException(ErrorCode.CONFLICT, message, details);
    }

    public static BusinessException forbiddenEx(String message, Map<String, Object> details) {
        return new BusinessException(ErrorCode.FORBIDDEN, message, details);
    }

    public static BusinessException invalidRequestEx(String message, Map<String, Object> details) {
        return new BusinessException(ErrorCode.INVALID_REQUEST, message, details);
    }

    // Reactor 체인 용 (Mono.error)
    public static <T> Mono<T> notFound(String message, Map<String, Object> details) {
        return Mono.error(notFoundEx(message, details));
    }

    public static <T> Mono<T> conflict(String message, Map<String, Object> details) {
        return Mono.error(conflictEx(message, details));
    }

    public static <T> Mono<T> forbidden(String message, Map<String, Object> details) {
        return Mono.error(forbiddenEx(message, details));
    }

    public static <T> Mono<T> invalidRequest(String message, Map<String, Object> details) {
        return Mono.error(invalidRequestEx(message, details));
    }
}

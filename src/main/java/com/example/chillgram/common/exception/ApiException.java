package com.example.chillgram.common.exception;

import java.util.Map;

/**
 * ErrorCode 기반으로 API 예외를 통제하는 공통 예외.
 * - ErrorCode: (httpStatus, defaultMessage) 중앙 관리
 * - detailMessage(getMessage): 서버 로그/추적용 (민감정보 금지)
 */
public class ApiException extends BusinessException {

    private ApiException(ErrorCode errorCode, String detailMessage, Map<String, Object> details) {
        super(errorCode, detailMessage, details);
    }

    public static ApiException of(ErrorCode errorCode, String detailMessage) {
        return new ApiException(errorCode, detailMessage, Map.of());
    }

    public static ApiException of(ErrorCode errorCode, String detailMessage, Map<String, Object> details) {
        return new ApiException(errorCode, detailMessage, details);
    }
}
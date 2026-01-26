package com.example.chillgram.common.exception;

/**
 * ErrorCode 기반으로 API 예외를 통제하는 공통 예외.
 * - ErrorCode: (httpStatus, defaultMessage) 중앙 관리
 * - detailMessage(getMessage): 서버 로그/추적용 (민감정보 금지)
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    private ApiException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }

    public static ApiException of(ErrorCode errorCode, String detailMessage) {
        return new ApiException(errorCode, detailMessage);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public int status() {
        return errorCode.httpStatus().value();
    }

    public String publicMessage() {
        return errorCode.defaultMessage();
    }
}

package com.example.chillgram.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // 400
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 유효하지 않습니다."),

    // auth (400) - 이메일 인증/토큰
    AUTH_EMAIL_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 링크입니다."),
    AUTH_EMAIL_ALREADY_VERIFIED(HttpStatus.BAD_REQUEST, "이미 이메일 인증이 완료되었습니다."),
    AUTH_ACCOUNT_DELETED(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 링크입니다."),

    // 401/403
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),

    // auth (401) - 로그인
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),

    // auth (403) - 계정 상태
    ACCOUNT_UNVERIFIED(HttpStatus.FORBIDDEN, "이메일 인증이 필요합니다."),
    ACCOUNT_DORMANT(HttpStatus.FORBIDDEN, "휴면 계정입니다. 휴면 해제가 필요합니다."),
    ACCOUNT_DELETED(HttpStatus.FORBIDDEN, "탈퇴 처리된 계정입니다."),

    // 404/409,
    AD_PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "광고 대상 제품을 찾을 수 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "요청이 현재 상태와 충돌합니다."),
    AD_GUIDE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "광고 가이드 생성에 실패했습니다."),

    AD_COPY_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "광고 문구 생성에 실패했습니다."),
    AD_GUIDE_REQUIRED(HttpStatus.BAD_REQUEST, "guideId는 필수입니다."),
    AD_COPIES_EMPTY(HttpStatus.INTERNAL_SERVER_ERROR, "광고 문구 생성 결과가 비어있습니다."),
    AI_UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "AI 서버 호출에 실패했습니다."),

    // 400
    AD_FOCUS_REQUIRED(HttpStatus.BAD_REQUEST, "adFocus는 필수입니다."),
    KEYWORDS_REQUIRED(HttpStatus.BAD_REQUEST, "selectedKeywords는 필수입니다."),
    PROJECT_TITLE_REQUIRED(HttpStatus.BAD_REQUEST, "projectTitle은 필수입니다."),
    AD_GOAL_REQUIRED(HttpStatus.BAD_REQUEST, "adGoal은 필수입니다."),

    // 502/500
    AI_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "AI 서비스가 비활성화되어 있습니다."),
    AI_CALL_FAILED(HttpStatus.BAD_GATEWAY, "AI 서버 호출에 실패했습니다."),
    AD_GUIDELINES_EMPTY(HttpStatus.INTERNAL_SERVER_ERROR, "가이드라인 생성 결과가 비어있습니다."),

    // auth (500) - 데이터 이상
    ACCOUNT_STATUS_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "계정 상태를 확인할 수 없습니다. 잠시 후 다시 시도해주세요."),

    // 502/503 (외부 연동)
    UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "외부 시스템 오류입니다."),
    UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "외부 시스템 응답이 지연되었습니다."),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류입니다.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
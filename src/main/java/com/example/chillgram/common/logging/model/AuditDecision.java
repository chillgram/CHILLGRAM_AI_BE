package com.example.chillgram.common.logging.model;

/**
 * 감사(Audit) 로그를 남길지 여부 및 어떤 핸들러/기능명으로 남길지에 대한 결정 결과.
 * - enabled=false면 감사 로그를 남기지 않는다.
 * - enabled=true면 feature/handler 정보를 이용해 감사 로그를 출력
 */
public record AuditDecision(
        boolean enabled,
        String feature,
        String handler
) {
    public static AuditDecision disabled(String handler) {
        return new AuditDecision(false, "-", handler);
    }

    public static AuditDecision enabled(String feature, String handler) {
        return new AuditDecision(true, feature, handler);
    }
}
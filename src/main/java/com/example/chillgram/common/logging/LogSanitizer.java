package com.example.chillgram.common.logging;

import org.springframework.stereotype.Component;

/**
 * 로그에 포함되는 입력값(RequestBody 등)에서 민감정보를 최소한으로 마스킹하는 컴포넌트.
 * - 정규식 마스킹은 완전하지 않다(키 변형/중첩 구조/배열 등 한계).
 * - 추후 화이트리스트(허용 필드만 로깅) 전략 필요 (개선 필요)
 */
@Component
public class LogSanitizer {
    public String sanitize(String body) {
        if (body == null) return null;
        return body
                .replaceAll("(?i)\"password\"\\s*:\\s*\".*?\"", "\"password\":\"***\"")
                .replaceAll("(?i)\"token\"\\s*:\\s*\".*?\"", "\"token\":\"***\"")
                .replaceAll("(?i)\"accessToken\"\\s*:\\s*\".*?\"", "\"accessToken\":\"***\"")
                .replaceAll("(?i)\"refreshToken\"\\s*:\\s*\".*?\"", "\"refreshToken\":\"***\"");
    }
}
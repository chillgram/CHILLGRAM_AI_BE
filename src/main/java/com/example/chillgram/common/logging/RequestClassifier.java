package com.example.chillgram.common.logging;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * 로깅 대상에서 제외할 요청을 판별하는 컴포넌트.
 * - 정적 리소스(css/js/image), favicon, actuator 등 운영에 불필요한 경로 제외
 * - SSE(Text-Event-Stream)는 연결이 길고 latency 의미가 깨질 수 있어 기본 제외
 */
@Component
public class RequestClassifier {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final List<String> EXCLUDE = List.of(
            "/actuator/**", "/favicon.ico", "/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg", "/**/*.jpeg", "/**/*.svg", "/**/*.ico"
    );

    public boolean shouldSkip(String rawPath, HttpHeaders headers) {
        if (rawPath != null) {
            for (String p : EXCLUDE) {
                if (MATCHER.match(p, rawPath)) return true;
            }
        }
        // SSE 제외(연결 오래감/무한 스트림)
        if (headers != null) {
            var accepts = headers.getAccept();
            if (accepts != null) {
                for (MediaType mt : accepts) {
                    if (MediaType.TEXT_EVENT_STREAM.includes(mt)) return true;
                }
            }
            MediaType ct = headers.getContentType();
            if (ct != null && MediaType.TEXT_EVENT_STREAM.includes(ct)) return true;
        }
        return false;
    }
}
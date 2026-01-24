package com.example.chillgram.common.logging;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 요청 바디(RequestBody) 캡처 정책.
 *
 * 목적:
 * - WebFlux RequestBody는 1회성 스트림이므로, 무작정 읽으면 컨트롤러 처리에 영향.
 * - 따라서 "캡처 대상"과 "최대 바이트"를 강하게 제한해야 운영에서 안전하다.
 *
 * 기본 정책:
 * - POST/PUT/PATCH만 대상
 * - JSON만 캡처
 * - multipart/SSE는 제외
 * - 최대 4KB(조정 가능)
 */
@Component
public class RequestBodyCapturePolicy {

    public int maxBytes() {
        return 4 * 1024; // 4KB
    }

    public boolean shouldCapture(HttpMethod method, HttpHeaders headers) {
        if (method == null) return false;
        if (!(method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH)) return false;

        MediaType ct = headers.getContentType();
        if (ct == null) return false;

        if (MediaType.MULTIPART_FORM_DATA.includes(ct) || MediaType.TEXT_EVENT_STREAM.includes(ct)) return false;

        return MediaType.APPLICATION_JSON.includes(ct)
                || Optional.ofNullable(ct.getSubtype()).orElse("").contains("json");
    }
}
package com.example.chillgram.common.logging;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.UUID;

/**
 * 요청 추적을 위한 traceId를 결정하는 컴포넌트.
 * - 우선순위: 요청 헤더(X-Request-Id) -> 없으면 UUID 생성
 * - 생성된 traceId는 exchange attributes에 저장되어 다른 컴포넌트/로그에서 재사용 가능.
 */
@Component
public class TraceIdResolver {
    public static final String TRACE_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_ATTR = "traceId";

    public String resolveOrCreate(ServerWebExchange exchange) {
        String fromHeader = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (fromHeader != null && !fromHeader.isBlank()) return fromHeader.trim();
        return UUID.randomUUID().toString();
    }
}
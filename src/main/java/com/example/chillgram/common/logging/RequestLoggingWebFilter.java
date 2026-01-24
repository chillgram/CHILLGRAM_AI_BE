package com.example.chillgram.common.logging;

import com.example.chillgram.common.logging.model.AuditDecision;
import com.example.chillgram.common.logging.model.RequestMeta;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.time.Duration;

/**
 * WebFlux 요청 로깅 오케스트레이터(단일 WebFilter).
 * 1) 전 요청 메타 로그(http): traceId/method/path/status/latency
 * 2) @AuditLog가 붙은 핸들러일 경우 감사 로그(audit)를 추가 출력
 *    - pathVars/queryParams 등 URL 기반 입력값
 *    - (정책 충족 시) request body 앞 N바이트 캡처 값(마스킹 후 출력)
 * - WebFlux에서 로깅을 여러 WebFilter로 쪼개면 순서 의존/중복/누락 리스크가 커짐
 * - 따라서 "필터는 1개"로 유지하고, 내부 로직을 컴포넌트로 분리해 유지보수성을 확보한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingWebFilter implements WebFilter {

    private final TraceIdResolver traceIdResolver;
    private final RequestClassifier classifier;
    private final AuditHandlerResolver auditResolver;
    private final RequestParamExtractor paramExtractor;
    private final RequestBodyCaptor bodyCaptor;
    private final LogSanitizer sanitizer;
    private final LogEmitter emitter;

    public RequestLoggingWebFilter(
            TraceIdResolver traceIdResolver,
            RequestClassifier classifier,
            AuditHandlerResolver auditResolver,
            RequestParamExtractor paramExtractor,
            RequestBodyCaptor bodyCaptor,
            LogSanitizer sanitizer,
            LogEmitter emitter
    ) {
        this.traceIdResolver = traceIdResolver;
        this.classifier = classifier;
        this.auditResolver = auditResolver;
        this.paramExtractor = paramExtractor;
        this.bodyCaptor = bodyCaptor;
        this.sanitizer = sanitizer;
        this.emitter = emitter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var req = exchange.getRequest();
        String path = req.getURI().getRawPath();

        if (classifier.shouldSkip(path, req.getHeaders())) {
            return chain.filter(exchange);
        }

        String traceId = traceIdResolver.resolveOrCreate(exchange);
        exchange.getAttributes().put(TraceIdResolver.TRACE_ID_ATTR, traceId);

        long startNs = System.nanoTime();

        ServerWebExchange decorated = bodyCaptor.decorateIfNeeded(exchange);

        Mono<Principal> principalMono = decorated.getPrincipal()
                .switchIfEmpty(Mono.just((Principal) () -> "anonymous"));

        return principalMono.flatMap(principal ->
                chain.filter(decorated)
                        .doFinally(st -> {
                            long latencyMs = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
                            int status = decorated.getResponse().getStatusCode() != null
                                    ? decorated.getResponse().getStatusCode().value()
                                    : 200;

                            var r = decorated.getRequest();
                            RequestMeta meta = new RequestMeta(
                                    traceId,
                                    String.valueOf(r.getMethod()),
                                    r.getURI().getRawPath(),
                                    status,
                                    latencyMs
                            );

                            MDC.put("traceId", traceId);
                            try {
                                emitter.emitHttp(meta);

                                AuditDecision audit = auditResolver.resolve(decorated);
                                if (audit.enabled()) {
                                    String user = safeUser(principal);
                                    String pv = paramExtractor.pathVars(decorated);
                                    String qp = paramExtractor.queryParams(decorated);

                                    String reqBody = bodyCaptor.readCapturedBody(decorated);
                                    String reqBodySanitized = sanitizer.sanitize(reqBody);

                                    emitter.emitAudit(meta, audit, user, pv, qp, reqBodySanitized);
                                }
                            } finally {
                                MDC.remove("traceId");
                            }
                        })
        );
    }

    private static String safeUser(Principal p) {
        if (p == null || p.getName() == null || p.getName().isBlank()) return "anonymous";
        return p.getName();
    }
}
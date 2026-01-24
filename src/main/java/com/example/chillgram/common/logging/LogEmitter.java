package com.example.chillgram.common.logging;

import com.example.chillgram.common.logging.model.AuditDecision;
import com.example.chillgram.common.logging.model.RequestMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 로그 출력 전용 컴포넌트.
 * - "무엇을 출력할지"는 다른 컴포넌트(Resolver/Extractor/Policy)가 결정
 */
@Component
public class LogEmitter {
    private static final Logger log = LoggerFactory.getLogger(LogEmitter.class);

    public void emitHttp(RequestMeta meta) {
        log.info("http traceId={} {} {} status={} latencyMs={}",
                meta.traceId(), meta.method(), meta.path(), meta.status(), meta.latencyMs());
    }

    public void emitAudit(RequestMeta meta,
                          AuditDecision audit,
                          String user,
                          String pathVars,
                          String queryParams,
                          String reqBodySanitized) {

        log.info("audit traceId={} feature={} user={} handler={} status={} latencyMs={} pathVars={} queryParams={} reqBody={}",
                meta.traceId(),
                audit.feature(),
                user,
                audit.handler(),
                meta.status(),
                meta.latencyMs(),
                pathVars,
                queryParams,
                reqBodySanitized
        );
    }
}
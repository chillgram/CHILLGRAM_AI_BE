package com.example.chillgram.common.logging;

import com.example.chillgram.common.logging.model.AuditDecision;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;

import java.util.Optional;

/**
 * 현재 요청이 어떤 핸들러(컨트롤러 메서드)로 매핑되었는지 확인하고,
 * 해당 메서드에 @AuditLog가 붙어있으면 감사 로그를 남기도록 결정한다.
 * - WebFlux에서는 BEST_MATCHING_HANDLER_ATTRIBUTE로 실제 핸들러를 확인 가능
 * - AOP 없이도 "실제 매핑된 핸들러 기준"으로 감사 로그를 선별할 수 있다.
 */
@Component
public class AuditHandlerResolver {

    public AuditDecision resolve(ServerWebExchange exchange) {
        Object handler = exchange.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (!(handler instanceof HandlerMethod hm)) {
            return AuditDecision.disabled("-");
        }

        AuditLog ann = hm.getMethodAnnotation(AuditLog.class);
        String handlerName = hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName();

        if (ann == null) return AuditDecision.disabled(handlerName);

        String feature = Optional.ofNullable(ann.value())
                .filter(s -> !s.isBlank())
                .orElse(hm.getMethod().getName());

        return AuditDecision.enabled(feature, handlerName);
    }
}
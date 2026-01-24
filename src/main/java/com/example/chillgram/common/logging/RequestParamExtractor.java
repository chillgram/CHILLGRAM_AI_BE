package com.example.chillgram.common.logging;

import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;

/**
 * URL 기반 입력값을 추출하는 컴포넌트.
 * - queryParams: ?a=1&b=2 같은 쿼리 파라미터
 * - pathVars: /users/{id} 처럼 템플릿 변수로 바인딩된 PathVariable 값
 * 주의:
 * - 폼/멀티파트는 여기서 다루지 않는다(용량/파일/민감정보 리스크).
 */
@Component
public class RequestParamExtractor {

    public String queryParams(ServerWebExchange ex) {
        MultiValueMap<String, String> qp = ex.getRequest().getQueryParams();
        if (qp == null || qp.isEmpty()) return null;
        return qp.toSingleValueMap().toString();
    }

    @SuppressWarnings("unchecked")
    public String pathVars(ServerWebExchange ex) {
        Map<String, String> vars = ex.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars == null || vars.isEmpty()) return null;
        return vars.toString();
    }
}

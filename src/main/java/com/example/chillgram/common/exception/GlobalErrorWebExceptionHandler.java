package com.example.chillgram.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorWebExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GlobalErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(@NonNull ServerWebExchange exchange, @NonNull Throwable ex) {
        var response = exchange.getResponse();

        // 이미 커밋된 응답이면 손댈 수 없음
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        ApiErrorResponse body = toErrorResponse(exchange.getRequest(), ex);

        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.setStatusCode(HttpStatus.valueOf(body.status()));

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception serializationEx) {
            // 직렬화 실패 시 최소 응답
            byte[] fallback = """
                    {"code":"INTERNAL_ERROR","message":"서버 오류입니다."}
                    """.getBytes(StandardCharsets.UTF_8);

            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(fallback)));
        }

        logByStatus(body, ex);

        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private void logByStatus(ApiErrorResponse body, Throwable ex) {
        String exType = ex.getClass().getSimpleName();

        // 5xx는 스택 포함
        if (body.status() >= 500) {
            log.error("Unhandled error: {} {} traceId={} code={} msg={} exType={}",
                    body.method(), body.path(), body.traceId(), body.code(), body.message(), exType, ex);
            return;
        }

        // 4xx는 스택 제외 (원인 추적을 위해 예외 타입은 남김)
        log.warn("Client error: {} {} traceId={} code={} msg={} exType={}",
                body.method(), body.path(), body.traceId(), body.code(), body.message(), exType);
    }

    private ApiErrorResponse toErrorResponse(ServerHttpRequest request, Throwable ex) {
        String path = request.getURI().getPath();
        String method = String.valueOf(request.getMethod());
        String traceId = resolveTraceId(request);

        // Validation (@Valid)
        if (ex instanceof WebExchangeBindException bindEx) {
            return validationError(path, method, traceId, bindEx);
        }

        // Business Exception
        if (ex instanceof BusinessException be) {
            return businessError(path, method, traceId, be);
        }

        // Framework status exceptions
        if (ex instanceof ResponseStatusException rse) {
            return statusError(path, method, traceId, rse.getStatusCode().value(), rse.getReason());
        }

        if (ex instanceof ErrorResponseException ere) {
            // ErrorResponseException은 reason을 직접 주기 애매해서 기본 메시지로 처리
            return statusError(path, method, traceId, ere.getStatusCode().value(), null);
        }

        // Fallback 500
        return ApiErrorResponse.of(
                ErrorCode.INTERNAL_ERROR.httpStatus().value(),
                ErrorCode.INTERNAL_ERROR.name(),
                ErrorCode.INTERNAL_ERROR.defaultMessage(),
                path, method, traceId,
                Map.of()
        );
    }

    private ApiErrorResponse validationError(String path, String method, String traceId, WebExchangeBindException bindEx) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : bindEx.getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fieldErrors", fieldErrors);

        ErrorCode code = ErrorCode.VALIDATION_FAILED;

        return ApiErrorResponse.of(
                code.httpStatus().value(),
                code.name(),
                code.defaultMessage(),
                path, method, traceId,
                details
        );
    }

    private ApiErrorResponse businessError(String path, String method, String traceId, BusinessException be) {
        ErrorCode code = be.errorCode();

        String message = (be.getMessage() == null || be.getMessage().isBlank())
                ? code.defaultMessage()
                : be.getMessage();

        return ApiErrorResponse.of(
                code.httpStatus().value(),
                code.name(),
                message,
                path, method, traceId,
                be.details()
        );
    }

    private ApiErrorResponse statusError(String path, String method, String traceId, int status, String reason) {
        ErrorCode code = mapStatusToErrorCode(status);

        String message = (reason == null || reason.isBlank())
                ? code.defaultMessage()
                : reason;

        return ApiErrorResponse.of(
                status,
                code.name(),
                message,
                path, method, traceId,
                Map.of()
        );
    }

    /**
     * status -> ErrorCode 매핑
     */
    private ErrorCode mapStatusToErrorCode(int status) {
        return switch (status) {
            case 400 -> ErrorCode.INVALID_REQUEST;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 409 -> ErrorCode.CONFLICT;
            case 502 -> ErrorCode.UPSTREAM_ERROR;
            case 504 -> ErrorCode.UPSTREAM_TIMEOUT;
            default -> (status >= 500) ? ErrorCode.INTERNAL_ERROR : ErrorCode.INVALID_REQUEST;
        };
    }

    private String resolveTraceId(ServerHttpRequest request) {
        String xRequestId = request.getHeaders().getFirst("X-Request-Id");
        if (xRequestId != null && !xRequestId.isBlank()) return xRequestId;

        String b3 = request.getHeaders().getFirst("X-B3-TraceId");
        if (b3 != null && !b3.isBlank()) return b3;

        String traceparent = request.getHeaders().getFirst("traceparent");
        if (traceparent != null && !traceparent.isBlank()) return traceparent;

        return null;
    }
}

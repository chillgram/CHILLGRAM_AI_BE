package com.example.chillgram.common.logging;

import com.example.chillgram.api.LoggingTestController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@WebFluxTest(
        controllers = LoggingTestController.class,
        excludeAutoConfiguration = ReactiveSecurityAutoConfiguration.class
)
@Import({
        RequestLoggingWebFilter.class,
        TraceIdResolver.class,
        RequestClassifier.class,
        AuditHandlerResolver.class,
        RequestParamExtractor.class,
        RequestBodyCapturePolicy.class,
        RequestBodyCaptor.class,
        LogSanitizer.class,
        LogEmitter.class
})
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingWebFilterTest {

    @jakarta.annotation.Resource
    WebTestClient webTestClient;

    @Test
    void http_meta_is_logged_for_every_request(CapturedOutput output) {
        webTestClient.get()
                .uri("/api/ping")
                .header("X-Request-Id", "rid-meta-1")
                .exchange()
                .expectStatus().isOk();

        String logs = output.getOut();
        assertThat(logs).contains("http traceId=rid-meta-1 GET /api/ping status=200 latencyMs=");
        assertThat(logs).doesNotContain("audit traceId=rid-meta-1");
    }

    @Test
    void audit_logs_include_pathVars_queryParams_and_reqBody(CapturedOutput output) {
        webTestClient.post()
                .uri("/api/orders/10?v=true")
                .header("X-Request-Id", "rid-audit-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"item\":\"a\"}")
                .exchange()
                .expectStatus().isOk();

        String logs = output.getOut();

        assertThat(logs).contains("http traceId=rid-audit-1 POST /api/orders/10 status=200 latencyMs=");
        assertThat(logs).contains("audit traceId=rid-audit-1 feature=CREATE_ORDER");
        assertThat(logs).contains("pathVars={id=10}");
        assertThat(logs).contains("queryParams={v=true}");
        assertThat(logs).contains("reqBody={\"item\":\"a\"}");
    }
}
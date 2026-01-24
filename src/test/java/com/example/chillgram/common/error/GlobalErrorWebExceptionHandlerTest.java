package com.example.chillgram.common.error;

import com.example.chillgram.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GlobalErrorWebExceptionHandlerTest {

    @Autowired
    WebTestClient webTestClient;

    @Test
    void businessException_with_message_and_details_returns_409() {
        webTestClient.get()
                .uri("/__test/errors/business")
                .header("X-Request-Id", "rid-123")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(409)
                .jsonPath("$.code").isEqualTo("CONFLICT")
                .jsonPath("$.message").isEqualTo("충돌 테스트")
                .jsonPath("$.path").isEqualTo("/__test/errors/business")
                .jsonPath("$.method").isEqualTo("GET")
                .jsonPath("$.traceId").isEqualTo("rid-123")
                .jsonPath("$.details.reason").isEqualTo("duplicate")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    void businessException_blank_message_uses_default_message() {
        webTestClient.get()
                .uri("/__test/errors/business-default-message")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(409)
                .jsonPath("$.code").isEqualTo("CONFLICT")
                .jsonPath("$.message").isEqualTo(ErrorCode.CONFLICT.defaultMessage())
                .jsonPath("$.path").isEqualTo("/__test/errors/business-default-message")
                .jsonPath("$.method").isEqualTo("GET")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    void validation_error_returns_400_and_fieldErrors() {
        webTestClient.post()
                .uri("/__test/errors/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.message").isEqualTo(ErrorCode.VALIDATION_FAILED.defaultMessage())
                .jsonPath("$.path").isEqualTo("/__test/errors/validation")
                .jsonPath("$.method").isEqualTo("POST")
                .jsonPath("$.details.fieldErrors.name").isEqualTo("name은 필수입니다.")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    void unhandled_exception_returns_500_internal_error() {
        webTestClient.get()
                .uri("/__test/errors/unhandled")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(500)
                .jsonPath("$.code").isEqualTo("INTERNAL_ERROR")
                .jsonPath("$.message").isEqualTo(ErrorCode.INTERNAL_ERROR.defaultMessage())
                .jsonPath("$.path").isEqualTo("/__test/errors/unhandled")
                .jsonPath("$.method").isEqualTo("GET")
                .jsonPath("$.timestamp").exists();
    }
}

package com.example.chillgram.domain.qa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

/**
 * Q&A 질문 작성 API 테스트
 * 
 * 실제 DB 연결 없이 API 엔드포인트와 요청 형식만 검증합니다.
 * 실제 동작 확인은 프론트엔드 연동 후 진행합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "60000")
class QaIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("🧪 [검증] 요청 형식 테스트 - 제목/내용 누락 시 400 에러")
    void createQuestion_ValidationError_Test() {
        // 제목과 내용 없이 요청하면 400 에러가 나야 함
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("category", "1");
        // title, content 누락

        webTestClient.post()
                .uri("/api/v1/qs/questions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Title and content are required");

        System.out.println("✅ 검증 통과: 필수값 누락 시 400 에러 반환 확인");
    }
}

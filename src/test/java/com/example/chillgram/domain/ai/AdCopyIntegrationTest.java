package com.example.chillgram.domain.ai;

import com.example.chillgram.domain.ai.dto.FinalCopyRequest;
import com.example.chillgram.domain.ai.dto.GuidelineRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "60000") // 넉넉하게 1분 대기
class AdCopyIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("🧪 [실험실] 1단계: 가이드라인 생성 및 결과 출력")
    void generateGuidelinesTest() {
        // ▼▼▼ 여기를 바꿔가며 실험하세요! ▼▼▼
        String productName = "새우깡";
        String keyword = "매운맛 챌린지";
        // ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲

        GuidelineRequest request = new GuidelineRequest(productName, keyword);

        System.out.println("\n========== [1단계 요청 시작] ==========");
        System.out.println("상품명: " + productName);
        System.out.println("키워드: " + keyword);
        System.out.println("=====================================\n");

        webTestClient.post()
                .uri("/api/v1/generate-guidelines")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(response -> {
                    String result = new String(response.getResponseBody(), StandardCharsets.UTF_8);
                    System.out.println("\n⭐⭐⭐ [1단계 결과] ⭐⭐⭐\n" + result + "\n⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐\n");
                });
    }

    @Test
    @DisplayName("🧪 [실험실] 2단계: 최종 카피 생성 및 결과 출력")
    void generateFinalCopyTest() {
        // ▼▼▼ 여기를 바꿔가며 실험하세요! ▼▼▼
        FinalCopyRequest request = new FinalCopyRequest(
                "새우깡",
                "매운맛 챌린지",
                "새우깡 맵부심 챌린지",
                "나만의 매운 소스 + 새우깡 조합",
                "유머러스한");
        // ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲

        System.out.println("\n========== [2단계 요청 시작] ==========");
        System.out.println("컨셉: " + request.selectedConcept());
        System.out.println("=====================================\n");

        webTestClient.post()
                .uri("/api/v1/generate-copy")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(response -> {
                    String result = new String(response.getResponseBody(), StandardCharsets.UTF_8);
                    System.out.println("\n✨✨✨ [2단계 결과] ✨✨✨\n" + result + "\n✨✨✨✨✨✨✨✨✨✨✨✨✨✨\n");
                });
    }
}

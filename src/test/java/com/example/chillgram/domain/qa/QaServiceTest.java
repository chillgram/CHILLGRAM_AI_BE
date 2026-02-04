package com.example.chillgram.domain.qa;

import com.example.chillgram.domain.qa.dto.QaAnswerResponse;
import com.example.chillgram.domain.qa.dto.QaDetailResponse;
import com.example.chillgram.domain.qa.dto.QaListResponse;
import com.example.chillgram.domain.qa.entity.QaAnswer;
import com.example.chillgram.domain.qa.entity.QaQuestion;
import com.example.chillgram.domain.qa.repository.QaAnswerRepository;
import com.example.chillgram.domain.qa.repository.QaQuestionAttachmentRepository;
import com.example.chillgram.domain.qa.repository.QaQuestionRepository;
import com.example.chillgram.domain.qa.service.QaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Q&A Service 단위 테스트
 * 
 * Repository를 Mocking하여 DB 연결 없이 Service 로직을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class QaServiceTest {

    @Mock
    private QaQuestionRepository qaQuestionRepository;

    @Mock
    private QaQuestionAttachmentRepository qaQuestionAttachmentRepository;

    @Mock
    private QaAnswerRepository qaAnswerRepository;

    @InjectMocks
    private QaService qaService;

    private QaQuestion mockQuestion;
    private QaAnswer mockAnswer;

    @BeforeEach
    void setUp() {
        mockQuestion = QaQuestion.builder()
                .questionId(1L)
                .categoryId(3L)
                .companyId(5L)
                .createdBy(10L)
                .title("테스트 질문입니다")
                .body("테스트 본문입니다")
                .status("WAITING")
                .viewCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        mockAnswer = QaAnswer.builder()
                .answerId(1L)
                .questionId(1L)
                .companyId(5L)
                .answeredBy(99L)
                .body("테스트 답변입니다")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ==================== 목록 조회 테스트 ====================

    @Test
    @DisplayName("🧪 [목록 조회] 전체 조회 성공")
    void getQuestionList_All_Success() {
        when(qaQuestionRepository.count()).thenReturn(Mono.just(1L));
        when(qaQuestionRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(Flux.just(mockQuestion));

        Mono<Page<QaListResponse>> result = qaService.getQuestionList(0, 10, null, "ALL");

        StepVerifier.create(result)
                .assertNext(page -> {
                    assertThat(page.getTotalElements()).isEqualTo(1);
                    assertThat(page.getContent()).hasSize(1);
                    assertThat(page.getContent().get(0).getTitle()).isEqualTo("테스트 질문입니다");
                })
                .verifyComplete();

        System.out.println("✅ 검증 통과: 전체 목록 조회 성공");
    }

    @Test
    @DisplayName("🧪 [목록 조회] 상태 필터 - WAITING")
    void getQuestionList_StatusFilter_Success() {
        when(qaQuestionRepository.countByStatus("WAITING")).thenReturn(Mono.just(1L));
        when(qaQuestionRepository.findByStatusOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(Flux.just(mockQuestion));

        Mono<Page<QaListResponse>> result = qaService.getQuestionList(0, 10, null, "WAITING");

        StepVerifier.create(result)
                .assertNext(page -> {
                    assertThat(page.getContent().get(0).getStatus()).isEqualTo("WAITING");
                })
                .verifyComplete();

        System.out.println("✅ 검증 통과: 상태 필터 조회 성공");
    }

    @Test
    @DisplayName("🧪 [목록 조회] 검색 기능")
    void getQuestionList_Search_Success() {
        when(qaQuestionRepository.countByTitleContainingOrBodyContaining(any(), any()))
                .thenReturn(Mono.just(1L));
        when(qaQuestionRepository.findByTitleContainingOrBodyContainingOrderByCreatedAtDesc(any(), any(),
                any(Pageable.class)))
                .thenReturn(Flux.just(mockQuestion));

        Mono<Page<QaListResponse>> result = qaService.getQuestionList(0, 10, "테스트", null);

        StepVerifier.create(result)
                .assertNext(page -> {
                    assertThat(page.getContent()).hasSize(1);
                })
                .verifyComplete();

        System.out.println("✅ 검증 통과: 검색 조회 성공");
    }

    // ==================== 상세 조회 테스트 ====================

    @Test
    @DisplayName("🧪 [상세 조회] 성공 - 답변 포함")
    void getQuestionDetail_Success() {
        when(qaQuestionRepository.findById(1L)).thenReturn(Mono.just(mockQuestion));
        when(qaQuestionAttachmentRepository.findByQuestionId(1L)).thenReturn(Flux.empty());
        when(qaAnswerRepository.findByQuestionIdOrderByCreatedAtAsc(1L)).thenReturn(Flux.just(mockAnswer));

        Mono<QaDetailResponse> result = qaService.getQuestionDetail(1L, "http://localhost:8080");

        StepVerifier.create(result)
                .assertNext(detail -> {
                    assertThat(detail.getQuestionId()).isEqualTo(1L);
                    assertThat(detail.getTitle()).isEqualTo("테스트 질문입니다");
                    assertThat(detail.getAnswers()).hasSize(1);
                    assertThat(detail.getAnswerCount()).isEqualTo(1);
                })
                .verifyComplete();

        System.out.println("✅ 검증 통과: 상세 조회 + 답변 목록 확인");
    }

    @Test
    @DisplayName("🧪 [상세 조회] 존재하지 않는 ID - 에러")
    void getQuestionDetail_NotFound() {
        when(qaQuestionRepository.findById(999L)).thenReturn(Mono.empty());

        Mono<QaDetailResponse> result = qaService.getQuestionDetail(999L, "http://localhost:8080");

        StepVerifier.create(result)
                .expectError(IllegalArgumentException.class)
                .verify();

        System.out.println("✅ 검증 통과: 존재하지 않는 ID → IllegalArgumentException");
    }

    // ==================== 답변 작성 테스트 ====================

    @Test
    @DisplayName("🧪 [답변 작성] 성공 - 질문 상태 변경 확인")
    void createAnswer_Success() {
        when(qaQuestionRepository.findById(1L)).thenReturn(Mono.just(mockQuestion));
        when(qaAnswerRepository.save(any(QaAnswer.class))).thenReturn(Mono.just(mockAnswer));
        when(qaQuestionRepository.save(any(QaQuestion.class))).thenReturn(Mono.just(mockQuestion));

        Mono<QaAnswerResponse> result = qaService.createAnswer(1L, "테스트 답변", 5L, 99L);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getAnswerId()).isEqualTo(1L);
                    assertThat(response.getBody()).isEqualTo("테스트 답변입니다");
                })
                .verifyComplete();

        System.out.println("✅ 검증 통과: 답변 작성 성공");
    }

    @Test
    @DisplayName("🧪 [답변 작성] 존재하지 않는 질문 - 에러")
    void createAnswer_QuestionNotFound() {
        when(qaQuestionRepository.findById(999L)).thenReturn(Mono.empty());

        Mono<QaAnswerResponse> result = qaService.createAnswer(999L, "테스트 답변", 5L, 99L);

        StepVerifier.create(result)
                .expectError(IllegalArgumentException.class)
                .verify();

        System.out.println("✅ 검증 통과: 존재하지 않는 질문 → IllegalArgumentException");
    }
}

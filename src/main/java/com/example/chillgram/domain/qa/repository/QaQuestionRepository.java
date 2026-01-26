package com.example.chillgram.domain.qa.repository;

import com.example.chillgram.domain.qa.entity.QaQuestion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface QaQuestionRepository extends ReactiveCrudRepository<QaQuestion, Long> {
    // 1. 전체 조회 (최신순)
    // 1. 전체 조회 (최신순)
    Flux<QaQuestion> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Mono<Long> count();

    // 2. 상태 필터 (최신순)
    Flux<QaQuestion> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Mono<Long> countByStatus(String status);

    // 3. 검색 (제목 or 내용) - 전체 상태
    Flux<QaQuestion> findByTitleContainingOrBodyContainingOrderByCreatedAtDesc(String titleKeyword, String bodyKeyword,
            Pageable pageable);

    Mono<Long> countByTitleContainingOrBodyContaining(String titleKeyword, String bodyKeyword);

    // 4. 검색 + 상태 필터
    Flux<QaQuestion> findByTitleContainingAndStatusOrBodyContainingAndStatusOrderByCreatedAtDesc(
            String titleKeyword, String status1,
            String bodyKeyword, String status2,
            Pageable pageable);

    Mono<Long> countByTitleContainingAndStatusOrBodyContainingAndStatus(
            String titleKeyword, String status1,
            String bodyKeyword, String status2);
}

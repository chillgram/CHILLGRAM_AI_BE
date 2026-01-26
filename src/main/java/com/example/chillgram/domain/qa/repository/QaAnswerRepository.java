package com.example.chillgram.domain.qa.repository;

import com.example.chillgram.domain.qa.entity.QaAnswer;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface QaAnswerRepository extends ReactiveCrudRepository<QaAnswer, Long> {
    // 질문 ID로 답변 목록 조회 (오래된 순)
    Flux<QaAnswer> findByQuestionIdOrderByCreatedAtAsc(Long questionId);

    // 질문 ID로 답변 개수 조회
    Mono<Long> countByQuestionId(Long questionId);
}

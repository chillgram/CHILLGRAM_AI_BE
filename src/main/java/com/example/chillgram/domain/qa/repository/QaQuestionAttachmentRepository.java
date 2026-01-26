package com.example.chillgram.domain.qa.repository;

import com.example.chillgram.domain.qa.entity.QaQuestionAttachment;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface QaQuestionAttachmentRepository extends ReactiveCrudRepository<QaQuestionAttachment, Long> {
    Flux<QaQuestionAttachment> findByQuestionId(Long questionId);
}

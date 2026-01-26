package com.example.chillgram.domain.qa.repository;

import com.example.chillgram.domain.qa.entity.QaQuestion;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QaQuestionRepository extends ReactiveCrudRepository<QaQuestion, Long> {
}

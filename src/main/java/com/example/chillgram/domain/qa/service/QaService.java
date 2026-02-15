package com.example.chillgram.domain.qa.service;

import com.example.chillgram.common.google.FileStorage;
import com.example.chillgram.common.google.FileStorage.StoredFile;
import com.example.chillgram.domain.qa.dto.QaAnswerResponse;
import com.example.chillgram.domain.qa.dto.QaDetailResponse;
import com.example.chillgram.domain.qa.dto.QaListResponse;
import com.example.chillgram.domain.qa.dto.QaWriteResponse;
import com.example.chillgram.domain.qa.entity.QaAnswer;
import com.example.chillgram.domain.qa.entity.QaQuestion;
import com.example.chillgram.domain.qa.repository.QaAnswerRepository;
import com.example.chillgram.domain.qa.repository.QaQuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QaService {

        private final QaQuestionRepository qaQuestionRepository;
        private final QaAnswerRepository qaAnswerRepository;
        private final com.example.chillgram.domain.user.repository.AppUserRepository appUserRepository;
        private final FileStorage fileStorage;

        // ==================== 목록 조회 ====================
        @Transactional(readOnly = true)
        public Mono<Page<QaListResponse>> getQuestionList(int page, int size, String search, String status) {
                Pageable pageable = PageRequest.of(page, size);

                boolean hasSearch = search != null && !search.isBlank();
                boolean hasStatus = status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status);

                Mono<Long> countMono;
                Flux<QaQuestion> listFlux;

                if (hasSearch && hasStatus) {
                        countMono = qaQuestionRepository.countByTitleContainingAndStatusOrBodyContainingAndStatus(
                                        search, status, search, status);
                        listFlux = qaQuestionRepository
                                        .findByTitleContainingAndStatusOrBodyContainingAndStatusOrderByCreatedAtDesc(
                                                        search, status, search, status, pageable);
                } else if (hasSearch) {
                        countMono = qaQuestionRepository.countByTitleContainingOrBodyContaining(search, search);
                        listFlux = qaQuestionRepository.findByTitleContainingOrBodyContainingOrderByCreatedAtDesc(
                                        search, search, pageable);
                } else if (hasStatus) {
                        countMono = qaQuestionRepository.countByStatus(status);
                        listFlux = qaQuestionRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
                } else {
                        countMono = qaQuestionRepository.count();
                        listFlux = qaQuestionRepository.findAllByOrderByCreatedAtDesc(pageable);
                }

                return Mono.zip(countMono, listFlux.collectList())
                                .flatMap(tuple -> {
                                        Long total = tuple.getT1();
                                        List<QaQuestion> list = tuple.getT2();

                                        // 1. 목록에 있는 모든 작성자 ID 수집
                                        List<Long> userIds = list.stream()
                                                        .map(QaQuestion::getCreatedBy)
                                                        .distinct()
                                                        .collect(Collectors.toList());

                                        // 2. ID로 이름 조회 (Bulk Fetch)
                                        return appUserRepository.findAllById(userIds)
                                                        .collectMap(com.example.chillgram.domain.user.domain.AppUser::getUserId,
                                                                        com.example.chillgram.domain.user.domain.AppUser::getName)
                                                        .map(nameMap -> {
                                                                List<QaListResponse> responses = list.stream()
                                                                                .map(q -> {
                                                                                        QaListResponse res = QaListResponse
                                                                                                        .from(q);
                                                                                        // 3. 이름 맵핑
                                                                                        res.setCreatedByName(nameMap
                                                                                                        .getOrDefault(q.getCreatedBy(),
                                                                                                                        "알 수 없음"));
                                                                                        return res;
                                                                                })
                                                                                .collect(Collectors.toList());
                                                                return new PageImpl<>(responses, pageable, total);
                                                        });
                                });
        }

        // ==================== 상세 조회 (답변 포함) ====================
        @Transactional(readOnly = true)
        public Mono<QaDetailResponse> getQuestionDetail(Long questionId) {
                return qaQuestionRepository.findById(questionId)
                                .flatMap(question -> {
                                        // 답변 조회
                                        Mono<List<QaAnswer>> answersMono = qaAnswerRepository
                                                        .findByQuestionIdOrderByCreatedAtAsc(questionId).collectList();

                                        // 질문 작성자 이름 조회
                                        Mono<String> creatorNameMono = appUserRepository
                                                        .findById(question.getCreatedBy())
                                                        .map(u -> u.getName() != null ? u.getName() : "알 수 없음")
                                                        .defaultIfEmpty("알 수 없음");

                                        return Mono.zip(answersMono, creatorNameMono)
                                                        .flatMap(tuple -> {
                                                                List<QaAnswer> answers = tuple.getT1();
                                                                String creatorName = tuple.getT2();

                                                                // 답변 작성자들의 이름 조회 준비
                                                                List<Long> answerUserIds = answers.stream()
                                                                                .map(QaAnswer::getAnsweredBy)
                                                                                .distinct()
                                                                                .collect(Collectors.toList());

                                                                return appUserRepository.findAllById(answerUserIds)
                                                                                .collectMap(com.example.chillgram.domain.user.domain.AppUser::getUserId,
                                                                                                com.example.chillgram.domain.user.domain.AppUser::getName)
                                                                                .map(nameMap -> {
                                                                                        QaDetailResponse response = QaDetailResponse
                                                                                                        .from(question, answers);
                                                                                        response.setCreatedByName(
                                                                                                        creatorName);

                                                                                        // 답변 DTO에도 이름 채워넣기
                                                                                        response.getAnswers().forEach(
                                                                                                        dto -> {
                                                                                                                dto.setAnsweredByName(
                                                                                                                                nameMap.getOrDefault(
                                                                                                                                                dto.getAnsweredBy(),
                                                                                                                                                "알 수 없음"));
                                                                                                        });
                                                                                        return response;
                                                                                });
                                                        });
                                })
                                .switchIfEmpty(Mono.error(
                                                new IllegalArgumentException("Question not found id=" + questionId)));
        }

        // ==================== 답변 작성 ====================
        @Transactional
        public Mono<QaAnswerResponse> createAnswer(Long questionId, String body, Long companyId, Long answeredBy) {
                // 1. 질문 존재 여부 확인
                return qaQuestionRepository.findById(questionId)
                                .switchIfEmpty(Mono.error(
                                                new IllegalArgumentException("Question not found id=" + questionId)))
                                .flatMap(question -> {
                                        // 2. 답변 저장
                                        QaAnswer answer = QaAnswer.builder()
                                                        .questionId(questionId)
                                                        .body(body)
                                                        .companyId(companyId)
                                                        .answeredBy(answeredBy)
                                                        .build();

                                        return qaAnswerRepository.save(answer)
                                                        .flatMap(savedAnswer -> {
                                                                // 3. 질문 상태를 ANSWERED로 변경 (첫 답변일 때)
                                                                if ("WAITING".equals(question.getStatus())) {
                                                                        // R2DBC는 setter 대신 새 객체를 만들어야 함
                                                                        QaQuestion updatedQuestion = QaQuestion
                                                                                        .builder()
                                                                                        .questionId(question
                                                                                                        .getQuestionId())
                                                                                        .companyId(question
                                                                                                        .getCompanyId())
                                                                                        .categoryId(question
                                                                                                        .getCategoryId())
                                                                                        .createdBy(question
                                                                                                        .getCreatedBy())
                                                                                        .title(question.getTitle())
                                                                                        .body(question.getBody())
                                                                                        .status("ANSWERED") // 상태 변경
                                                                                        .viewCount(question
                                                                                                        .getViewCount())
                                                                                        .createdAt(question
                                                                                                        .getCreatedAt())
                                                                                        .updatedAt(question
                                                                                                        .getUpdatedAt() != null
                                                                                                                        ? question.getUpdatedAt()
                                                                                                                        : question.getCreatedAt()) // null이면
                                                                                                                                                   // created_at
                                                                                                                                                   // 사용
                                                                                        .answeredAt(java.time.LocalDateTime
                                                                                                        .now()) // 첫 답변
                                                                                                                // 시간
                                                                                        .gcsImageUrl(question
                                                                                                        .getGcsImageUrl()) // 기존
                                                                                                                           // URL
                                                                                                                           // 유지
                                                                                        .build();
                                                                        return qaQuestionRepository
                                                                                        .save(updatedQuestion)
                                                                                        .thenReturn(savedAnswer);
                                                                }
                                                                return Mono.just(savedAnswer);
                                                        });
                                })
                                .flatMap(savedAnswer -> {
                                        // 작성자 이름 조회 후 응답 생성
                                        return appUserRepository.findById(answeredBy)
                                                        .map(u -> u.getName() != null ? u.getName() : "알 수 없음")
                                                        .defaultIfEmpty("알 수 없음")
                                                        .map(name -> {
                                                                QaAnswerResponse resp = QaAnswerResponse
                                                                                .from(savedAnswer);
                                                                resp.setAnsweredByName(name);
                                                                return resp;
                                                        });
                                })
                                .doOnSuccess(
                                                resp -> log.info("Answer created: questionId={}, answerId={}",
                                                                questionId, resp.getAnswerId()))
                                .doOnError(e -> log.error("Failed to create answer", e));
        }

        // ==================== 질문 작성 ====================
        @Transactional
        public Mono<QaWriteResponse> createQuestion(String title, String content, Long categoryId, Long companyId,
                        Long createdBy, FilePart filePart) {
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                QaQuestion question = QaQuestion.builder()
                                .title(title)
                                .body(content)
                                .categoryId(categoryId)
                                .companyId(companyId)
                                .createdBy(createdBy)
                                .status("WAITING")
                                .createdAt(now)
                                .updatedAt(now) // 생성 시점에는 created_at과 동일
                                .build();

                return qaQuestionRepository.save(question)
                                .flatMap(savedQuestion -> {
                                        if (filePart != null && !filePart.filename().isBlank()) {
                                                return saveAttachment(savedQuestion.getQuestionId(), filePart)
                                                                .flatMap(attachment -> {
                                                                        // URL 업데이트 후 다시 저장
                                                                        QaQuestion updated = QaQuestion.builder()
                                                                                        .questionId(savedQuestion
                                                                                                        .getQuestionId())
                                                                                        .companyId(savedQuestion
                                                                                                        .getCompanyId())
                                                                                        .categoryId(savedQuestion
                                                                                                        .getCategoryId())
                                                                                        .createdBy(savedQuestion
                                                                                                        .getCreatedBy())
                                                                                        .title(savedQuestion.getTitle())
                                                                                        .body(savedQuestion.getBody())
                                                                                        .status(savedQuestion
                                                                                                        .getStatus())
                                                                                        .viewCount(savedQuestion
                                                                                                        .getViewCount())
                                                                                        .createdAt(savedQuestion
                                                                                                        .getCreatedAt())
                                                                                        .updatedAt(savedQuestion
                                                                                                        .getUpdatedAt())
                                                                                        .gcsImageUrl(attachment
                                                                                                        .fileUrl()) // URL
                                                                                                                    // 저장
                                                                                        .build();
                                                                        return qaQuestionRepository.save(updated);
                                                                });
                                        }
                                        return Mono.just(savedQuestion);
                                })
                                .map(QaWriteResponse::from);
                // 작성자 이름은 createQuestion 응답(QaWriteResponse)에는 포함하지 않음 (요구사항 없음)
                // 필요하다면 추가 가능
        }

        // ==================== 첨부파일 저장 (GCS) ====================
        private Mono<StoredFile> saveAttachment(Long questionId, FilePart filePart) {
                return fileStorage.store(filePart, "qna/" + questionId)
                                .doOnSuccess(stored -> log.info("Attachment uploaded to GCS: questionId={}, url={}",
                                                questionId,
                                                stored.fileUrl()))
                                .doOnError(e -> log.error("File upload failed", e));
        }

        // ==================== 질문 수정 ====================
        @Transactional
        public Mono<QaWriteResponse> updateQuestion(Long questionId, String title, String content,
                        Long categoryId, String status, Long userId, FilePart filePart) {
                return qaQuestionRepository.findById(questionId)
                                .switchIfEmpty(Mono.error(
                                                com.example.chillgram.common.exception.ApiException.of(
                                                                com.example.chillgram.common.exception.ErrorCode.NOT_FOUND,
                                                                "질문을 찾을 수 없습니다. id=" + questionId)))
                                .flatMap(question -> {
                                        // 작성자 본인 확인
                                        if (!question.getCreatedBy().equals(userId)) {
                                                return Mono.error(
                                                                com.example.chillgram.common.exception.ApiException.of(
                                                                                com.example.chillgram.common.exception.ErrorCode.FORBIDDEN,
                                                                                "본인이 작성한 질문만 수정할 수 있습니다."));
                                        }

                                        // categoryId가 null이면 기존값 유지
                                        Long finalCategoryId = categoryId != null ? categoryId
                                                        : question.getCategoryId();

                                        // status가 null이거나 비어있으면 기존값 유지
                                        String finalStatus = (status != null && !status.isBlank()) ? status
                                                        : question.getStatus();

                                        // title이 null이거나 비어있으면 기존값 유지
                                        String finalTitle = (title != null && !title.isBlank()) ? title
                                                        : question.getTitle();

                                        // content가 null이거나 비어있으면 기존값 유지
                                        String finalContent = (content != null && !content.isBlank()) ? content
                                                        : question.getBody();

                                        // 수정된 질문 생성 (R2DBC는 불변 객체)
                                        QaQuestion.QaQuestionBuilder builder = QaQuestion.builder()
                                                        .questionId(question.getQuestionId())
                                                        .companyId(question.getCompanyId())
                                                        .categoryId(finalCategoryId)
                                                        .createdBy(question.getCreatedBy())
                                                        .title(finalTitle)
                                                        .body(finalContent)
                                                        .status(finalStatus)
                                                        .viewCount(question.getViewCount())
                                                        .createdAt(question.getCreatedAt())
                                                        .updatedAt(java.time.LocalDateTime.now())
                                                        .answeredAt(question.getAnsweredAt())
                                                        .gcsImageUrl(question.getGcsImageUrl()); // 기존 URL 유지

                                        return Mono.just(builder);
                                })
                                .flatMap(builder -> {
                                        // 새 첨부파일이 있으면 업로드 후 URL 업데이트
                                        if (filePart != null && !filePart.filename().isBlank()) {
                                                return saveAttachment(builder.build().getQuestionId(), filePart)
                                                                .map(att -> builder.gcsImageUrl(att.fileUrl())
                                                                                .build());
                                        }
                                        return Mono.just(builder.build());
                                })
                                .flatMap(qaQuestionRepository::save) // 최종 저장
                                .map(QaWriteResponse::from)
                                .doOnSuccess(resp -> log.info("Question updated: id={}", questionId))
                                .doOnError(e -> log.error("Failed to update question", e));
        }

        // ==================== 답변 수정 ====================
        @Transactional
        public Mono<QaAnswerResponse> updateAnswer(Long answerId, String body, Long userId) {
                return qaAnswerRepository.findById(answerId)
                                .switchIfEmpty(Mono.error(
                                                com.example.chillgram.common.exception.ApiException.of(
                                                                com.example.chillgram.common.exception.ErrorCode.NOT_FOUND,
                                                                "답변을 찾을 수 없습니다. id=" + answerId)))
                                .flatMap(answer -> {
                                        // 작성자 본인 확인
                                        if (!answer.getAnsweredBy().equals(userId)) {
                                                return Mono.error(
                                                                com.example.chillgram.common.exception.ApiException.of(
                                                                                com.example.chillgram.common.exception.ErrorCode.FORBIDDEN,
                                                                                "본인이 작성한 답변만 수정할 수 있습니다."));
                                        }

                                        // 수정된 답변 생성
                                        QaAnswer updatedAnswer = QaAnswer.builder()
                                                        .answerId(answer.getAnswerId())
                                                        .questionId(answer.getQuestionId())
                                                        .companyId(answer.getCompanyId())
                                                        .answeredBy(answer.getAnsweredBy())
                                                        .body(body)
                                                        .createdAt(answer.getCreatedAt())
                                                        .updatedAt(java.time.LocalDateTime.now()) // 수정 시간 갱신
                                                        .build();

                                        return qaAnswerRepository.save(updatedAnswer);
                                })
                                .flatMap(savedAnswer -> {
                                        // 작성자 이름 조회 후 응답 생성
                                        return appUserRepository.findById(userId)
                                                        .map(u -> u.getName() != null ? u.getName() : "알 수 없음")
                                                        .defaultIfEmpty("알 수 없음")
                                                        .map(name -> {
                                                                QaAnswerResponse resp = QaAnswerResponse
                                                                                .from(savedAnswer);
                                                                resp.setAnsweredByName(name);
                                                                return resp;
                                                        });
                                })
                                .doOnSuccess(resp -> log.info("Answer updated: id={}", answerId))
                                .doOnError(e -> log.error("Failed to update answer", e));
        }

}

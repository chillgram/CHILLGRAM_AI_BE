package com.example.chillgram.domain.qa.service;

import com.example.chillgram.domain.qa.dto.QaAnswerResponse;
import com.example.chillgram.domain.qa.dto.QaDetailResponse;
import com.example.chillgram.domain.qa.dto.QaListResponse;
import com.example.chillgram.domain.qa.dto.QaWriteResponse;
import com.example.chillgram.domain.qa.entity.QaAnswer;
import com.example.chillgram.domain.qa.entity.QaQuestion;
import com.example.chillgram.domain.qa.entity.QaQuestionAttachment;
import com.example.chillgram.domain.qa.repository.QaAnswerRepository;
import com.example.chillgram.domain.qa.repository.QaQuestionAttachmentRepository;
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

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QaService {

        private final QaQuestionRepository qaQuestionRepository;
        private final QaQuestionAttachmentRepository qaQuestionAttachmentRepository;
        private final QaAnswerRepository qaAnswerRepository; // 추가됨

        private static final String UPLOAD_DIR = "src/main/resources/static/qna/";

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
                                .map(tuple -> {
                                        Long total = tuple.getT1();
                                        List<QaQuestion> list = tuple.getT2();
                                        List<QaListResponse> responses = list.stream()
                                                        .map(QaListResponse::from)
                                                        .collect(Collectors.toList());
                                        return new PageImpl<>(responses, pageable, total);
                                });
        }

        // ==================== 상세 조회 (답변 포함) ====================
        @Transactional(readOnly = true)
        public Mono<QaDetailResponse> getQuestionDetail(Long questionId) {
                return qaQuestionRepository.findById(questionId)
                                .flatMap(question -> {
                                        // 첨부파일 + 답변 동시 조회
                                        Mono<List<QaQuestionAttachment>> attachmentsMono = qaQuestionAttachmentRepository
                                                        .findByQuestionId(questionId).collectList();
                                        Mono<List<QaAnswer>> answersMono = qaAnswerRepository
                                                        .findByQuestionIdOrderByCreatedAtAsc(questionId).collectList();

                                        return Mono.zip(attachmentsMono, answersMono)
                                                        .map(tuple -> QaDetailResponse.from(question, tuple.getT1(),
                                                                        tuple.getT2()));
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
                                                                                        .build();
                                                                        return qaQuestionRepository
                                                                                        .save(updatedQuestion)
                                                                                        .thenReturn(savedAnswer);
                                                                }
                                                                return Mono.just(savedAnswer);
                                                        });
                                })
                                .map(QaAnswerResponse::from)
                                .doOnSuccess(
                                                resp -> log.info("Answer created: questionId={}, answerId={}",
                                                                questionId, resp.getAnswerId()))
                                .doOnError(e -> log.error("Failed to create answer", e));
        }

        // ==================== 질문 작성 ====================
        @Transactional
        public Mono<QaWriteResponse> createQuestion(String title, String content, Long categoryId, Long companyId,
                        Long createdBy, FilePart filePart) {
                QaQuestion question = QaQuestion.builder()
                                .title(title)
                                .body(content)
                                .categoryId(categoryId)
                                .companyId(companyId)
                                .createdBy(createdBy)
                                .status("WAITING")
                                .build();

                return qaQuestionRepository.save(question)
                                .flatMap(savedQuestion -> {
                                        if (filePart != null && !filePart.filename().isBlank()) {
                                                return saveAttachment(savedQuestion.getQuestionId(), filePart)
                                                                .thenReturn(savedQuestion);
                                        }
                                        return Mono.just(savedQuestion);
                                })
                                .map(QaWriteResponse::from);
        }

        // ==================== 첨부파일 저장 ====================
        private Mono<QaQuestionAttachment> saveAttachment(Long questionId, FilePart filePart) {
                File uploadDir = new File(UPLOAD_DIR);
                if (!uploadDir.exists()) {
                        boolean created = uploadDir.mkdirs();
                        if (created) {
                                log.info("Created upload directory: {}", uploadDir.getAbsolutePath());
                        }
                }

                String originalFilename = filePart.filename();
                String safeFilename = UUID.randomUUID() + "_" + originalFilename;
                String filePath = Paths.get(UPLOAD_DIR, safeFilename).toString();

                String mimeType = filePart.headers().getContentType() != null
                                ? filePart.headers().getContentType().toString()
                                : "application/octet-stream";

                return filePart.transferTo(Paths.get(filePath))
                                .then(Mono.defer(() -> {
                                        long fileSize = new File(filePath).length();

                                        // DB에는 웹 접근 경로로 저장 (/qna/파일명)
                                        String webPath = "/qna/" + safeFilename;

                                        QaQuestionAttachment attachment = QaQuestionAttachment.builder()
                                                        .questionId(questionId)
                                                        .fileUrl(webPath)
                                                        .mimeType(mimeType)
                                                        .fileSize(fileSize)
                                                        .build();

                                        return qaQuestionAttachmentRepository.save(attachment);
                                }))
                                .doOnSuccess(att -> log.info("Attachment saved: questionId={}, file={}", questionId,
                                                filePath))
                                .doOnError(e -> log.error("File upload failed", e));
        }
}

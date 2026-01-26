package com.example.chillgram.domain.qa.service;

import com.example.chillgram.domain.qa.dto.QaWriteResponse;
import com.example.chillgram.domain.qa.entity.QaQuestion;
import com.example.chillgram.domain.qa.entity.QaQuestionAttachment;
import com.example.chillgram.domain.qa.repository.QaQuestionAttachmentRepository;
import com.example.chillgram.domain.qa.repository.QaQuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.io.File;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QaService {

    private final QaQuestionRepository qaQuestionRepository;
    private final QaQuestionAttachmentRepository qaQuestionAttachmentRepository;

    // 로컬 파일 저장 경로 (실제 운영 환경에서는 S3 등을 사용 권장)
    private static final String UPLOAD_DIR = "uploads/";

    @Transactional
    public Mono<QaWriteResponse> createQuestion(String title, String content, Long categoryId, Long companyId,
            Long createdBy, FilePart filePart) {
        // 1. 질문 먼저 저장
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
                    // 2. 파일이 있으면 첨부파일 테이블에 저장
                    if (filePart != null && !filePart.filename().isBlank()) {
                        return saveAttachment(savedQuestion.getQuestionId(), filePart)
                                .thenReturn(savedQuestion);
                    }
                    return Mono.just(savedQuestion);
                })
                .map(QaWriteResponse::from);
    }

    private Mono<QaQuestionAttachment> saveAttachment(Long questionId, FilePart filePart) {
        // 업로드 디렉토리 생성
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        String originalFilename = filePart.filename();
        String safeFilename = UUID.randomUUID() + "_" + originalFilename;
        String filePath = Paths.get(UPLOAD_DIR, safeFilename).toString();

        // 파일 타입 추출
        String mimeType = filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString()
                : "application/octet-stream";

        return filePart.transferTo(Paths.get(filePath))
                .then(Mono.defer(() -> {
                    // 파일 크기 계산
                    long fileSize = new File(filePath).length();

                    QaQuestionAttachment attachment = QaQuestionAttachment.builder()
                            .questionId(questionId)
                            .fileUrl(filePath)
                            .mimeType(mimeType)
                            .fileSize(fileSize)
                            .build();

                    return qaQuestionAttachmentRepository.save(attachment);
                }))
                .doOnSuccess(att -> log.info("Attachment saved: questionId={}, file={}", questionId, filePath))
                .doOnError(e -> log.error("File upload failed", e));
    }
}

package com.example.chillgram.domain.project.controller;

import com.example.chillgram.common.google.FileStorage;
import com.example.chillgram.domain.project.entity.ProjectImageAttachment;
import com.example.chillgram.domain.project.repository.ProjectImageAttachmentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Project", description = "프로젝트 관리 API")
public class ProjectImageController {

    private final FileStorage fileStorage;
    private final ProjectImageAttachmentRepository projectImageAttachmentRepository;

    /**
     * 프로젝트 이미지 업로드 (GCS)
     */
    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "프로젝트 이미지 업로드", description = "프로젝트의 Base Image를 GCS에 업로드합니다.")
    @ApiResponse(responseCode = "201", description = "이미지 업로드 성공")
    @ApiResponse(responseCode = "400", description = "파일이 첨부되지 않음")
    public Mono<ProjectImageAttachment> uploadProjectImage(
            @Parameter(description = "프로젝트 ID", required = true) @PathVariable("id") Long projectId,
            @Parameter(description = "업로드할 이미지 파일", required = true) @RequestPart("file") FilePart file) {

        log.info("Project image upload request: projectId={}, filename={}", projectId, file.filename());

        return fileStorage.store(file, "projects")
                .flatMap(stored -> {
                    ProjectImageAttachment attachment = ProjectImageAttachment.builder()
                            .projectId(projectId)
                            .fileUrl(stored.fileUrl())
                            .fileName(file.filename())
                            .fileSize(stored.fileSize())
                            .mimeType(stored.mimeType())
                            .build();
                    return projectImageAttachmentRepository.save(attachment);
                })
                .doOnSuccess(att -> log.info("Image upload successful: {}", att.getFileUrl()));
    }
}

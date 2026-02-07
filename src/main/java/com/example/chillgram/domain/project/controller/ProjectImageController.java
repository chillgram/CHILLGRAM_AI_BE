package com.example.chillgram.domain.project.controller;

import com.example.chillgram.domain.project.entity.ProjectImageAttachment;
import com.example.chillgram.domain.project.service.ProjectService;
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

import java.util.Map;

/**
 * 프로젝트 이미지 업로드 Controller
 * (Controller 방식 예시 - 다른 Project API는 Router/Handler 방식 유지)
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Project", description = "프로젝트 관리 API")
public class ProjectImageController {

    private final ProjectService projectService;

    /**
     * 프로젝트 이미지 업로드
     * 
     * @param projectId 프로젝트 ID
     * @param file      업로드할 이미지 파일
     * @return 업로드된 이미지 정보
     */
    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "프로젝트 이미지 업로드", description = "프로젝트의 Base Image를 업로드합니다. (Controller 방식)")
    @ApiResponse(responseCode = "201", description = "이미지 업로드 성공")
    @ApiResponse(responseCode = "400", description = "파일이 첨부되지 않음")
    @ApiResponse(responseCode = "500", description = "파일 업로드 실패")
    public Mono<ProjectImageAttachment> uploadProjectImage(
            @Parameter(description = "프로젝트 ID", required = true) @PathVariable("id") Long projectId,

            @Parameter(description = "업로드할 이미지 파일", required = true) @RequestPart("file") FilePart file) {

        log.info("Project image upload request: projectId={}, filename={}", projectId, file.filename());

        return projectService.saveProjectImage(projectId, file)
                .doOnSuccess(attachment -> log.info("Image upload successful: {}", attachment.getFileUrl()))
                .doOnError(e -> log.error("Image upload failed", e));
    }

    /**
     * 에러 핸들링 (선택 사항)
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<Map<String, String>> handleException(Exception e) {
        log.error("Controller error", e);
        return Mono.just(Map.of("error", "파일 업로드 실패: " + e.getMessage()));
    }
}

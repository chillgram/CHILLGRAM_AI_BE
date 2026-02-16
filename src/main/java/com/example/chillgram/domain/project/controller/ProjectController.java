package com.example.chillgram.domain.project.controller;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.common.google.FileStorage;
import com.example.chillgram.common.google.GcsFileStorage;
import com.example.chillgram.domain.advertising.dto.jobs.CreateJobRequest;
import com.example.chillgram.domain.advertising.dto.jobs.JobEnums;
import com.example.chillgram.domain.ai.service.JobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.example.chillgram.common.security.AuthPrincipal;
import com.example.chillgram.domain.project.dto.ProjectCreateRequest;
import com.example.chillgram.domain.project.dto.ProjectResponse;
import com.example.chillgram.domain.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Project", description = "프로젝트 관리 API")
public class ProjectController {

    private final GcsFileStorage gcsFileStorage;
    private final JobService jobService;
    private final ObjectMapper om;
    private final ProjectService projectService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "프로젝트 생성")
    public Mono<ProjectResponse> createProject(
            @RequestParam Long productId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ProjectCreateRequest request) {

        if (principal == null) {
            throw ApiException.of(ErrorCode.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        return projectService.createProject(productId, principal.companyId(), principal.userId(), request);
    }

    @PostMapping(value = "/{projectId}/basic-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "이미지 업로드 및 미리보기 작업 생성", description = "이미지를 업로드하고 AI 미리보기 작업을 생성합니다.")
    public Mono<Map<String, Object>> createPreviewJob(
            @PathVariable long projectId,
            @RequestPart("payload") String payloadJson,
            @RequestPart("file") FilePart file) {
        // 1) payload parse
        Mono<ObjectNode> payloadMono = Mono.fromCallable(() -> (ObjectNode) om.readTree(payloadJson))
                .onErrorMap(e -> ApiException.of(ErrorCode.VALIDATION_FAILED, "payload must be valid JSON"));

        // 2) input image tmp 업로드 (중요: tmp/inputs prefix로 분리)
        Mono<FileStorage.StoredFile> storedMono = gcsFileStorage.store(file, "tmp/inputs");

        // 3) payload에 inputUri(gs://...) 넣고 job 요청
        return Mono.zip(payloadMono, storedMono)
                .flatMap(t -> {
                    ObjectNode payload = t.getT1();
                    FileStorage.StoredFile stored = t.getT2();

                    if (stored.gsUri() == null || stored.gsUri().isBlank()) {
                        return Mono.error(ApiException.of(ErrorCode.INTERNAL_ERROR, "gsUri missing in StoredFile"));
                    }

                    payload.put("inputUri", stored.gsUri());
                    if (!payload.has("n"))
                        payload.put("n", 3);

                    // 너희가 말한 프롬프트 옵션들(예시): payload에 그대로 담겨있다고 가정
                    // style/background/logoPosition/productNameStyle/removeText...

                    CreateJobRequest req = new CreateJobRequest(JobEnums.JobType.BASIC, payload);

                    return jobService.requestJob(projectId, req, "");
                })
                .map(jobId -> Map.of("jobId", jobId.toString(), "status", "PENDING"));
    }

}
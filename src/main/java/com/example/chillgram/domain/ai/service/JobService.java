package com.example.chillgram.domain.ai.service;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.common.google.GcsFileStorage;
import com.example.chillgram.domain.advertising.dto.jobs.CreateJobRequest;
import com.example.chillgram.domain.advertising.dto.jobs.JobEnums;
import com.example.chillgram.domain.advertising.dto.jobs.JobEnums.JobStatus;
import com.example.chillgram.domain.advertising.dto.jobs.JobResponse;
import com.example.chillgram.domain.advertising.dto.jobs.JobResultRequest;
import com.example.chillgram.domain.ai.repository.JobTaskRepository;
import com.example.chillgram.domain.ai.repository.OutboxEventRepository;
import com.example.chillgram.domain.project.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Slf4j
public class JobService {

    private final JobTaskRepository jobRepo;
    private final OutboxEventRepository outboxRepo;
    private final TransactionalOperator tx;
    private final ObjectMapper om;
    private final String jobsRoutingKey;

    private final com.example.chillgram.domain.content.service.ContentService contentService;
    private final ProjectRepository projectRepository;
    private final GcsFileStorage gcs;

    public JobService(
            JobTaskRepository jobRepo,
            OutboxEventRepository outboxRepo,
            TransactionalOperator tx,
            ObjectMapper om,
            @Value("${app.jobs.routing-key}") String jobsRoutingKey,
            com.example.chillgram.domain.content.service.ContentService contentService,
            ProjectRepository projectRepository,
            GcsFileStorage gcs
    ) {
        this.jobRepo = jobRepo;
        this.outboxRepo = outboxRepo;
        this.tx = tx;
        this.om = om;
        this.jobsRoutingKey = jobsRoutingKey;
        this.contentService = contentService;
        this.projectRepository = projectRepository;
        this.gcs = gcs;
    }

    public Mono<UUID> requestJob(long projectId, CreateJobRequest req, String traceId) {
        UUID jobId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        ObjectNode eventPayload = om.createObjectNode();
        eventPayload.put("jobId", jobId.toString());
        eventPayload.put("projectId", projectId);
        eventPayload.put("jobType", req.jobType().name());
        eventPayload.set("payload", req.payload());
        eventPayload.put("requestedAt", now.toString());
        eventPayload.put("traceId", traceId == null ? "" : traceId);

        return tx.transactional(
                jobRepo.insertRequested(jobId, projectId, req.jobType(), req.payload(), now)
                        .then(outboxRepo.insertOutbox(
                                outboxId,
                                "JOB",
                                jobId,
                                "JOB_REQUESTED",
                                jobsRoutingKey,
                                eventPayload,
                                now))
                        .thenReturn(jobId)
        );
    }

    public Mono<JobResponse> getJob(UUID jobId) {
        return jobRepo.findById(jobId)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.JOB_NOT_FOUND, "job not found id=" + jobId)));
    }

    /**
     * Worker 결과 반영.
     * - outputUri: gcs.toPublicUrl로 https 정규화 후 저장
     * - JobType별로 Content/Project에 side-effect 반영 후 job_task 상태 업데이트
     */
    public Mono<Void> applyResult(UUID jobId, JobResultRequest req) {
        OffsetDateTime now = OffsetDateTime.now();

        return jobRepo.findById(jobId)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.JOB_NOT_FOUND, "job not found id=" + jobId)))
                .flatMap(existing -> {

                    // 멱등
                    if (existing.status() == JobStatus.SUCCEEDED || existing.status() == JobStatus.FAILED) {
                        return Mono.empty();
                    }

                    JsonNode pl = existing.payload();

                    // 실패 처리
                    if (!req.success()) {
                        String ec = (req.errorCode() == null || req.errorCode().isBlank())
                                ? "WORKER_FAILED" : req.errorCode();
                        String em = (req.errorMessage() == null) ? "" : req.errorMessage();

                        Mono<Void> failSideEffect = Mono.empty();
                        if (pl != null && pl.has("contentId")) {
                            long contentId = pl.get("contentId").asLong();
                            // ✅ 실패 처리 통일 (ContentService에서 안전하게 처리)
                            failSideEffect = contentService.markContentFailed(contentId);
                        } else if (existing.jobType() == JobEnums.JobType.DIELINE && pl != null && pl.has("projectId")) {
                            long projectId = pl.get("projectId").asLong();
                            log.info("Project mockup generation failed. projectId={}, jobId={}, reason={}",
                                    projectId, jobId, em);
                        }

                        return tx.transactional(
                                failSideEffect
                                        .then(jobRepo.markFailed(jobId, ec, em, now))
                                        .then()
                        ).then();
                    }

                    // success=true 검증
                    if (req.outputUri() == null || req.outputUri().isBlank()) {
                        return Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED,
                                "outputUri is required when success=true"));
                    }

                    // ✅ HTTPS 정규화 (이미 https면 pass-through)
                    String normalized = gcs.toPublicUrl(req.outputUri());

                    Long contentId = (pl != null && pl.has("contentId")) ? pl.get("contentId").asLong() : null;
                    Integer bannerRatio = (pl != null && pl.has("bannerRatio")) ? pl.get("bannerRatio").asInt() : null;

                    // ✅ jobType별 sideEffect
                    Mono<Void> sideEffect = Mono.empty();
                    JobEnums.JobType type = existing.jobType();

                    if (type == JobEnums.JobType.DIELINE) {
                        // DIELINE: projectId 우선, 없으면 contentId로 처리
                        if (pl != null && pl.has("projectId")) {
                            long projectId = pl.get("projectId").asLong();
                            sideEffect = projectRepository.findById(projectId)
                                    .flatMap(project -> {
                                        project.applyMockupResult(normalized);
                                        return projectRepository.save(project);
                                    })
                                    .switchIfEmpty(Mono.fromRunnable(() -> log.warn(
                                            "Project not found when applying mockup result. projectId={}, jobId={}",
                                            projectId, jobId)))
                                    .then();
                        } else if (contentId != null) {
                            // ✅ DIELINE을 Content로 저장할 때는 "목업 결과"로 처리(너희 엔티티 정의 기준)
                            sideEffect = contentService.updateMockupResult(contentId, normalized).then();
                        } else {
                            log.warn("DIELINE succeeded but no projectId/contentId in payload. jobId={}", jobId);
                        }

                    } else {
                        // SNS/VIDEO/BANNER: contentId 기반 저장
                        if (contentId != null) {
                            sideEffect = switch (type) {
                                case SNS, VIDEO -> contentService.applyMediaResult(contentId, normalized).then();
                                case BANNER -> contentService.applyBannerResult(contentId, normalized, bannerRatio).then();
                                default -> Mono.empty();
                            };
                        } else {
                            log.warn("Job succeeded but no contentId in payload. jobId={}, type={}", jobId, type);
                        }
                    }

                    // ✅ 트랜잭션: sideEffect -> markSucceeded
                    return tx.transactional(
                            sideEffect
                                    .then(jobRepo.markSucceeded(jobId, normalized, now))
                                    .then()
                    ).then();
                });
    }
}
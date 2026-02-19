package com.example.chillgram.domain.ai.service;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.domain.advertising.dto.jobs.CreateJobRequest;
import com.example.chillgram.domain.advertising.dto.jobs.JobEnums;
import com.example.chillgram.domain.advertising.dto.jobs.JobEnums.JobStatus;
import com.example.chillgram.domain.advertising.dto.jobs.JobResponse;
import com.example.chillgram.domain.advertising.dto.jobs.JobResultRequest;
import com.example.chillgram.domain.ai.repository.JobTaskRepository;
import com.example.chillgram.domain.ai.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import lombok.extern.slf4j.Slf4j;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.example.chillgram.domain.project.repository.ProjectRepository; // Added import

@Service
@Slf4j
public class JobService {

    private final JobTaskRepository jobRepo;
    private final OutboxEventRepository outboxRepo;
    private final TransactionalOperator tx;
    private final ObjectMapper om;
    private final String jobsRoutingKey;
    private final com.example.chillgram.domain.content.service.ContentService contentService;
    private final ProjectRepository projectRepository; // Added field

    private final com.example.chillgram.common.google.GcsFileStorage gcs;

    public JobService(
            JobTaskRepository jobRepo,
            OutboxEventRepository outboxRepo,
            TransactionalOperator tx,
            ObjectMapper om,
            @Value("${app.jobs.routing-key}") String jobsRoutingKey,
            com.example.chillgram.domain.content.service.ContentService contentService,
            ProjectRepository projectRepository,
            com.example.chillgram.common.google.GcsFileStorage gcs) {
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
                        .thenReturn(jobId));
    }

    public Mono<JobResponse> getJob(UUID jobId) {
        return jobRepo.findById(jobId)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.JOB_NOT_FOUND, "job not found id=" + jobId)));
    }

    /**
     * Worker 결과 반영. outputUri를 HTTPS로 정규화하여 DB에 저장.
     */
    public Mono<Void> applyResult(UUID jobId, JobResultRequest req) {
        OffsetDateTime now = OffsetDateTime.now();

        return jobRepo.findById(jobId)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.JOB_NOT_FOUND, "job not found id=" + jobId)))
                .flatMap(existing -> {
                    if (existing.status() == JobStatus.SUCCEEDED || existing.status() == JobStatus.FAILED) {
                        return Mono.empty(); // 멱등: 무시
                    }

                    if (req.success()) {
                        if (req.outputUri() == null || req.outputUri().isBlank()) {
                            return Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED,
                                    "outputUri is required when success=true"));
                        }

                        // HTTPS 정규화 (이미 HTTPS면 pass-through)
                        String normalized = gcs.toPublicUrl(req.outputUri());

                        // [Fix] Job 성공 시 Content 업데이트 (DIELINE + SNS/BANNER/VIDEO)
                        Mono<Void> sideEffect = Mono.empty();
                        JobEnums.JobType type = existing.jobType();

                        // 1. Content 기반 업데이트 (DIELINE, SNS, BANNER, VIDEO 모두 해당)
                        if (existing.payload() != null && existing.payload().has("contentId")) {
                            long contentId = existing.payload().get("contentId").asLong();
                            // 이름 변경된 메서드 호출 (updateMockupResult -> updateUrlFromJob)
                            sideEffect = contentService.updateUrlFromJob(contentId, normalized).then();

                        } else if (type == JobEnums.JobType.DIELINE && existing.payload() != null
                                && existing.payload().has("projectId")) {
                            // 2. Project 기반 업데이트 (DIELINE fallback)
                            long projectId = existing.payload().get("projectId").asLong();
                            sideEffect = projectRepository.findById(projectId)
                                    .flatMap(project -> {
                                        project.applyMockupResult(normalized);
                                        return projectRepository.save(project);
                                    })
                                    .switchIfEmpty(Mono.fromRunnable(() -> log.warn(
                                            "Project not found when applying mockup result. projectId={}, jobId={}",
                                            projectId, jobId)))
                                    .then();
                        } else {
                            if (type == JobEnums.JobType.DIELINE || type == JobEnums.JobType.SNS
                                    || type == JobEnums.JobType.BANNER || type == JobEnums.JobType.VIDEO) {
                                log.warn("Job completed but no contentId or projectId in payload. jobId={}, type={}",
                                        jobId, type);
                            }
                        }

                        // [Refactor] 트랜잭션 보장 & 실행 순서 변경 (SideEffect -> MarkSucceeded)
                        return tx.transactional(
                                sideEffect.then(jobRepo.markSucceeded(jobId, normalized, now))).then();

                    } else {
                        String ec = (req.errorCode() == null || req.errorCode().isBlank()) ? "WORKER_FAILED"
                                : req.errorCode();
                        String em = (req.errorMessage() == null) ? "" : req.errorMessage();

                        // [Fix] Job 실패 시 처리 (contentId 우선)
                        Mono<Void> failSideEffect = Mono.empty();
                        JsonNode pl = existing.payload();

                        if (pl != null && pl.has("contentId")) {
                            long contentId = pl.get("contentId").asLong();
                            // Content 기반 실패 처리 (모든 Job Type 공통)
                            failSideEffect = contentService.updateMockupFailed(contentId).then();
                        } else if (existing.jobType() == JobEnums.JobType.DIELINE && pl != null
                                && pl.has("projectId")) {
                            long projectId = pl.get("projectId").asLong();
                            // Project 기반 실패 시 로그만 (fallback - DIELINE legacy)
                            log.info("Project mockup generation failed. projectId={}, reason={}", projectId, em);
                        }

                        return tx.transactional(
                                failSideEffect.then(jobRepo.markFailed(jobId, ec, em, now).then())).then();
                    }
                });
    }

}
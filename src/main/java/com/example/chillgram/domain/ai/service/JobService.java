package com.example.chillgram.domain.ai.service;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.domain.advertising.dto.jobs.CreateJobRequest;
import com.example.chillgram.domain.advertising.dto.jobs.JobEnums.JobStatus;
import com.example.chillgram.domain.advertising.dto.jobs.JobResponse;
import com.example.chillgram.domain.advertising.dto.jobs.JobResultRequest;
import com.example.chillgram.domain.ai.repository.JobTaskRepository;
import com.example.chillgram.domain.ai.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class JobService {

    private final JobTaskRepository jobRepo;
    private final OutboxEventRepository outboxRepo;
    private final TransactionalOperator tx;
    private final ObjectMapper om;
    private final String jobsRoutingKey;

    public JobService(
            JobTaskRepository jobRepo,
            OutboxEventRepository outboxRepo,
            TransactionalOperator tx,
            ObjectMapper om,
            @Value("${app.jobs.routing-key}") String jobsRoutingKey
    ) {
        this.jobRepo = jobRepo;
        this.outboxRepo = outboxRepo;
        this.tx = tx;
        this.om = om;
        this.jobsRoutingKey = jobsRoutingKey;
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
                                now
                        ))
                        .thenReturn(jobId)
        );
    }

    public Mono<JobResponse> getJob(UUID jobId) {
        return jobRepo.findById(jobId)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.JOB_NOT_FOUND, "job not found id=" + jobId)));
    }

    /**
     * 리팩토링 포인트:
     * - MQ는 중복 전달 가능 → 이미 SUCCEEDED/FAILED면 "에러"가 아니라 그냥 무시(멱등).
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
                            return Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED, "outputUri is required when success=true"));
                        }
                        return jobRepo.markSucceeded(jobId, req.outputUri(), now).then();
                    } else {
                        String ec = (req.errorCode() == null || req.errorCode().isBlank()) ? "WORKER_FAILED" : req.errorCode();
                        String em = (req.errorMessage() == null) ? "" : req.errorMessage();
                        return jobRepo.markFailed(jobId, ec, em, now).then();
                    }
                });
    }
}
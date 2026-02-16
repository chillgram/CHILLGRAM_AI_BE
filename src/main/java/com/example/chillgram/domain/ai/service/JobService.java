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

    // ✅ gs:// -> https:// 변환용 (GcsFileStorage와 동일한 개념)
    private final String gcsBucket;
    private final String gcsPublicBaseUrl;

    public JobService(
            JobTaskRepository jobRepo,
            OutboxEventRepository outboxRepo,
            TransactionalOperator tx,
            ObjectMapper om,
            @Value("${app.jobs.routing-key}") String jobsRoutingKey,
            @Value("${gcs.bucket}") String gcsBucket,
            @Value("${gcs.publicBaseUrl}") String gcsPublicBaseUrl
    ) {
        this.jobRepo = jobRepo;
        this.outboxRepo = outboxRepo;
        this.tx = tx;
        this.om = om;
        this.jobsRoutingKey = jobsRoutingKey;
        this.gcsBucket = gcsBucket;
        this.gcsPublicBaseUrl = stripTrailingSlash(gcsPublicBaseUrl);
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
     * ✅ 핵심 수정:
     * - success=true면 outputUri(gs:// or https://)를 "프론트가 쓸 수 있는 값"으로 정규화해서 저장
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

                        // ✅ gs://면 https로 변환해서 저장
                        String normalized = normalizeOutputUri(req.outputUri());

                        return jobRepo.markSucceeded(jobId, normalized, now).then();
                    } else {
                        String ec = (req.errorCode() == null || req.errorCode().isBlank()) ? "WORKER_FAILED" : req.errorCode();
                        String em = (req.errorMessage() == null) ? "" : req.errorMessage();
                        return jobRepo.markFailed(jobId, ec, em, now).then();
                    }
                });
    }

    private String normalizeOutputUri(String outputUri) {
        String u = outputUri.trim();

        // 이미 https면 그대로
        if (u.startsWith("http://") || u.startsWith("https://")) return u;

        // gs://bucket/object -> publicBaseUrl/object 로 변환
        if (u.startsWith("gs://")) {
            String noScheme = u.substring("gs://".length());
            int slash = noScheme.indexOf('/');
            if (slash < 0) return u; // 이상한 값이면 그냥 저장(= 디버깅 목적)

            String bucket = noScheme.substring(0, slash);
            String object = noScheme.substring(slash + 1);

            // bucket이 예상과 다르면 그대로 두는 게 낫다(잘못 변환 방지)
            if (gcsBucket != null && !gcsBucket.isBlank() && !bucket.equals(gcsBucket)) {
                return u;
            }
            return gcsPublicBaseUrl + "/" + object;
        }

        // 그 외 스킴은 그대로
        return u;
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
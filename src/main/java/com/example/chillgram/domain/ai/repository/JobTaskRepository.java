package com.example.chillgram.domain.ai.repository;

import com.example.chillgram.domain.advertising.dto.jobs.JobEnums.JobStatus;
import com.example.chillgram.domain.advertising.dto.jobs.JobEnums.JobType;
import com.example.chillgram.domain.advertising.dto.jobs.JobResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public class JobTaskRepository {

    private final DatabaseClient db;
    private final ObjectMapper om;

    public JobTaskRepository(DatabaseClient db, ObjectMapper om) {
        this.db = db;
        this.om = om;
    }

    public Mono<Void> insertRequested(UUID jobId, long projectId, JobType jobType, JsonNode payload, OffsetDateTime now) {
        return db.sql("""
                insert into job_task(job_id, project_id, job_type, status, payload, requested_at, updated_at)
                values (:jobId, :projectId, :jobType, :status, cast(:payload as jsonb), :now, :now)
                """)
                .bind("jobId", jobId)
                .bind("projectId", projectId)
                .bind("jobType", jobType.name())
                .bind("status", JobStatus.REQUESTED.name())
                .bind("payload", payload.toString())
                .bind("now", now)
                .fetch().rowsUpdated().then();
    }

    public Mono<JobResponse> findById(UUID jobId) {
        return db.sql("""
                select job_id, project_id, job_type, status, payload, output_uri, error_code, error_message, requested_at, updated_at
                from job_task
                where job_id = :jobId
                """)
                .bind("jobId", jobId)
                .map((row, meta) -> {
                    var payloadStr = row.get("payload", String.class);
                    JsonNode payload = null;
                    try { payload = payloadStr == null ? null : om.readTree(payloadStr); } catch (Exception ignored) {}

                    return new JobResponse(
                            row.get("job_id", UUID.class),
                            row.get("project_id", Long.class),
                            JobType.valueOf(row.get("job_type", String.class)),
                            JobStatus.valueOf(row.get("status", String.class)),
                            payload,
                            row.get("output_uri", String.class),
                            row.get("error_code", String.class),
                            row.get("error_message", String.class),
                            row.get("requested_at", OffsetDateTime.class),
                            row.get("updated_at", OffsetDateTime.class)
                    );
                })
                .one();
    }

    public Mono<Long> markSucceeded(UUID jobId, String outputUri, OffsetDateTime now) {
        return db.sql("""
                update job_task
                set status = :succeeded,
                    output_uri = :outputUri,
                    error_code = null,
                    error_message = null,
                    updated_at = :now
                where job_id = :jobId and status in (:requested, :running)
                """)
                .bind("succeeded", JobStatus.SUCCEEDED.name())
                .bind("outputUri", outputUri)
                .bind("now", now)
                .bind("jobId", jobId)
                .bind("requested", JobStatus.REQUESTED.name())
                .bind("running", JobStatus.RUNNING.name())
                .fetch().rowsUpdated();
    }

    public Mono<Long> markFailed(UUID jobId, String errorCode, String errorMessage, OffsetDateTime now) {
        return db.sql("""
                update job_task
                set status = :failed,
                    output_uri = null,
                    error_code = :errorCode,
                    error_message = :errorMessage,
                    updated_at = :now
                where job_id = :jobId and status in (:requested, :running)
                """)
                .bind("failed", JobStatus.FAILED.name())
                .bind("errorCode", errorCode)
                .bind("errorMessage", errorMessage)
                .bind("now", now)
                .bind("jobId", jobId)
                .bind("requested", JobStatus.REQUESTED.name())
                .bind("running", JobStatus.RUNNING.name())
                .fetch().rowsUpdated();
    }
}
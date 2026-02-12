package com.example.chillgram.domain.advertising.dto.jobs;

import com.example.chillgram.domain.advertising.dto.jobs.JobEnums.JobStatus;
import com.example.chillgram.domain.advertising.dto.jobs.JobEnums.JobType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JobResponse(
        UUID jobId,
        long projectId,
        JobType jobType,
        JobStatus status,
        JsonNode payload,
        String outputUri,
        String errorCode,
        String errorMessage,
        OffsetDateTime requestedAt,
        OffsetDateTime updatedAt
) {}
package com.example.chillgram.domain.advertising.dto.jobs;

import com.example.chillgram.domain.advertising.dto.jobs.JobEnums.JobType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record CreateJobRequest(
        @NotNull JobType jobType,
        @NotNull JsonNode payload
) {}
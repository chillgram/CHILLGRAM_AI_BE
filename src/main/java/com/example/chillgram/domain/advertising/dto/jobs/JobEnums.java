package com.example.chillgram.domain.advertising.dto.jobs;

public final class JobEnums {
    private JobEnums() {}

    public enum JobType { BANNER, SNS, VIDEO, DIELINE, BASIC }
    public enum JobStatus { REQUESTED, RUNNING, SUCCEEDED, FAILED }
}

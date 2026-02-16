package com.example.chillgram.common.google;

import java.util.List;
import java.util.Map;

public record BasicImageManifest(
        List<Candidate> candidates
) {
    public record Candidate(
            int id,
            String label,
            String gsUri,
            Map<String, Object> meta
    ) {}
}
package com.example.chillgram.common.google;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class BasicImageManifestService {

    private final GcsFileStorage gcs;
    private final ObjectMapper om;

    public BasicImageManifestService(GcsFileStorage gcs, ObjectMapper om) {
        this.gcs = gcs;
        this.om = om;
    }

    public Mono<BasicImageManifest> readManifest(String manifestGsUri) {
        return gcs.fetchBytes(manifestGsUri)
                .map(bytes -> {
                    try {
                        return om.readValue(bytes, BasicImageManifest.class);
                    } catch (Exception e) {
                        throw new RuntimeException("manifest json parse failed", e);
                    }
                });
    }
}
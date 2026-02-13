package com.example.chillgram.common.google;

import com.google.cloud.storage.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;

/**
 * GCS 파일 업로드 구현체
 * 여러 도메인에서 공통으로 사용 (광고, 프로젝트, 프로필 등)
 */
@Component
public class GcsFileStorage implements FileStorage {

    private final Storage storage;
    private final String bucket;
    private final String publicBaseUrl;

    public GcsFileStorage(
            Storage storage,
            @Value("${gcs.bucket}") String bucket,
            @Value("${gcs.publicBaseUrl}") String publicBaseUrl) {
        this.storage = storage;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    public Mono<StoredFile> store(FilePart filePart) {
        return store(filePart, "ads");
    }

    @Override
    public Mono<StoredFile> store(FilePart filePart, String folder) {
        String safeName = filePart.filename().replaceAll("[\\\\/\\r\\n]", "_");
        String objectName = folder + "/" + Instant.now().toEpochMilli()
                + "_" + UUID.randomUUID().toString().replace("-", "")
                + "_" + safeName;

        return Mono.usingWhen(
                Mono.fromCallable(() -> Files.createTempFile("upload_", "_" + safeName))
                        .subscribeOn(Schedulers.boundedElastic()),
                temp -> filePart.transferTo(temp)
                        .then(Mono.fromCallable(() -> {
                            BlobId blobId = BlobId.of(bucket, objectName);
                            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                                    .setContentType(
                                            filePart.headers().getContentType() != null
                                                    ? filePart.headers().getContentType().toString()
                                                    : "application/octet-stream")
                                    .build();

                            storage.create(blobInfo, Files.readAllBytes(temp));

                            String url = publicBaseUrl + "/" + objectName;

                            return new StoredFile(
                                    url,
                                    blobInfo.getContentType(),
                                    Files.size(temp));
                        }).subscribeOn(Schedulers.boundedElastic())),
                temp -> Mono.fromRunnable(() -> {
                    try {
                        Files.deleteIfExists(temp);
                    } catch (Exception ignored) {
                    }
                }).subscribeOn(Schedulers.boundedElastic()));
    }
}

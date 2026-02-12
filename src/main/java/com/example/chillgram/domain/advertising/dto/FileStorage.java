package com.example.chillgram.domain.advertising.dto;

import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public interface FileStorage {
    Mono<StoredFile> store(FilePart filePart);

    record StoredFile(
            String fileUrl,
            String thumbUrl,
            String mimeType,
            Long fileSize,
            Integer width,
            Integer height
    ) {}
}
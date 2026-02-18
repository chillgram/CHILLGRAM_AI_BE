package com.example.chillgram.common.google;

import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

/**
 * 파일 저장소 인터페이스 (GCS 등)
 * 여러 도메인에서 공통으로 사용
 */
public interface FileStorage {
    Mono<StoredFile> store(FilePart filePart);

    /**
     * 지정된 폴더에 파일 저장
     * 
     * @param filePart 업로드할 파일
     * @param folder   GCS 버킷 내 폴더 경로 (예: "ads", "projects", "profiles")
     */
    Mono<StoredFile> store(FilePart filePart, String folder);

    record StoredFile(
            String fileUrl,
            String mimeType,
            String gsUri,
            Long fileSize) {
    }

    /**
     * Delete object identified by gs:// URI or HTTPS URL.
     *
     * @param uri gs://... or https://... URL
     */
    Mono<Void> delete(String uri);
}

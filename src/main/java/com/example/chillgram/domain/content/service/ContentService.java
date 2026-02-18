package com.example.chillgram.domain.content.service;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.domain.content.dto.ContentAssetResponse;
import com.example.chillgram.domain.content.dto.ContentResponse;
import com.example.chillgram.domain.content.dto.ContentUpdateRequest;
import com.example.chillgram.domain.content.entity.Content;
import com.example.chillgram.domain.content.entity.ContentAsset;
import com.example.chillgram.domain.content.repository.ContentAssetRepository;
import com.example.chillgram.domain.content.repository.ContentRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@lombok.extern.slf4j.Slf4j
public class ContentService {

    private final ContentRepository contentRepository;
    private final ContentAssetRepository contentAssetRepository;
    private final com.example.chillgram.common.google.GcsFileStorage gcs;

    public ContentService(ContentRepository contentRepository, ContentAssetRepository contentAssetRepository,
            com.example.chillgram.common.google.GcsFileStorage gcs) {
        this.contentRepository = contentRepository;
        this.contentAssetRepository = contentAssetRepository;
        this.gcs = gcs;
    }

    // ============================
    // Content 조회
    // ============================

    public Flux<ContentResponse> getContentsByProject(Long projectId) {
        return contentRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .map(this::toResponse);
    }

    public Mono<ContentResponse> getContentById(Long contentId) {
        return contentRepository.findById(contentId)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.NOT_FOUND, "콘텐츠를 찾을 수 없습니다.")))
                .map(this::toResponse);
    }

    // ============================
    // Content 수정
    // ============================

    public Mono<ContentResponse> updateContent(Long contentId, ContentUpdateRequest req) {
        return contentRepository.findById(contentId)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.NOT_FOUND, "콘텐츠를 찾을 수 없습니다.")))
                .flatMap(content -> {
                    content.update(req.title(), req.body(), req.status(), req.tags(), req.platform());
                    return contentRepository.save(content);
                })
                .map(this::toResponse);
    }

    // ============================
    // Content 삭제
    // ============================

    public Mono<Void> deleteContent(Long contentId) {
        return contentRepository.findById(contentId)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.NOT_FOUND, "콘텐츠를 찾을 수 없습니다.")))
                .flatMap(content ->
                // 에셋도 함께 삭제
                contentAssetRepository.findByContentIdOrderBySortOrderAsc(contentId)
                        .flatMap(contentAssetRepository::delete)
                        .then(contentRepository.delete(content)));
    }

    // ============================
    // Mockup 업데이트 (from JobService)
    // ============================

    public Mono<Content> updateMockupResult(Long contentId, String generatedImgUrl) {
        return contentRepository.findById(contentId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.error("Content not found for mockup result: {}", contentId);
                    return Mono.error(
                            ApiException.of(ErrorCode.NOT_FOUND, "Content not found for mockup result: " + contentId));
                }))
                .flatMap(content -> {
                    content.updateMockup(generatedImgUrl);
                    return contentRepository.save(content);
                });
    }

    public Mono<Content> updateMockupFailed(Long contentId) {
        return contentRepository.findById(contentId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.error("Content not found for mockup failure: {}", contentId);
                    return Mono.error(
                            ApiException.of(ErrorCode.NOT_FOUND, "Content not found for mockup failure: " + contentId));
                }))
                .flatMap(content -> {
                    content.updateMockupFailed();
                    return contentRepository.save(content);
                });
    }

    // ============================
    // ContentAsset 조회
    // ============================

    public Flux<ContentAssetResponse> getAssetsByContent(Long contentId) {
        return contentAssetRepository.findByContentIdOrderBySortOrderAsc(contentId)
                .map(this::toAssetResponse);
    }

    // ============================
    // ContentAsset 삭제
    // ============================

    public Mono<Void> deleteAsset(Long assetId) {
        return contentAssetRepository.findById(assetId)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.NOT_FOUND, "에셋을 찾을 수 없습니다.")))
                .flatMap(contentAssetRepository::delete);
    }

    // ============================
    // Mapper
    // ============================

    private ContentResponse toResponse(Content c) {
        return new ContentResponse(
                c.getId(), c.getCompanyId(), c.getProductId(), c.getProjectId(),
                c.getContentType(), c.getPlatform(),
                c.getTitle(), c.getBody(), c.getStatus(), c.getTags(),
                c.getViewCount(), c.getLikeCount(), c.getShareCount(),
                c.getBannerRatio(),
                gcs.toPublicUrl(c.getGcsImgUrl()), gcs.toPublicUrl(c.getMockupImgUrl()),
                c.getCreatedAt(), c.getUpdatedAt());
    }

    private ContentAssetResponse toAssetResponse(ContentAsset a) {
        return new ContentAssetResponse(
                a.getId(), a.getContentId(), a.getAssetType(),
                gcs.toPublicUrl(a.getFileUrl()), gcs.toPublicUrl(a.getThumbUrl()), a.getMimeType(),
                a.getFileSize(), a.getWidth(), a.getHeight(),
                a.getDurationMs(), a.getSortOrder(), a.getCreatedAt());
    }
}

package com.example.chillgram.domain.content.service;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.common.google.GcsFileStorage;
import com.example.chillgram.domain.content.dto.ContentAssetResponse;
import com.example.chillgram.domain.content.dto.ContentResponse;
import com.example.chillgram.domain.content.dto.ContentUpdateRequest;
import com.example.chillgram.domain.content.entity.Content;
import com.example.chillgram.domain.content.entity.ContentAsset;
import com.example.chillgram.domain.content.repository.ContentAssetRepository;
import com.example.chillgram.domain.content.repository.ContentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class ContentService {

    private final ContentRepository contentRepository;
    private final ContentAssetRepository contentAssetRepository;
    private final GcsFileStorage gcs;

    public ContentService(ContentRepository contentRepository,
                          ContentAssetRepository contentAssetRepository,
                          GcsFileStorage gcs) {
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
                        contentAssetRepository.findByContentIdOrderBySortOrderAsc(contentId)
                                .flatMap(contentAssetRepository::delete)
                                .then(contentRepository.delete(content))
                );
    }

    // ============================
    // Mockup 업데이트 (from JobService)
    // ============================

    /**
     * 패키지 목업 결과 업데이트: mockup_img_url 갱신
     */
    public Mono<Content> updateMockupResult(Long contentId, String mockupImgUrl) {
        return contentRepository.findById(contentId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.error("Content not found for mockup result: {}", contentId);
                    return Mono.error(ApiException.of(ErrorCode.NOT_FOUND,
                            "Content not found for mockup result: " + contentId));
                }))
                .flatMap(content -> {
                    content.updateMockup(mockupImgUrl);
                    return contentRepository.save(content);
                });
    }

    public Mono<Content> updateMockupFailed(Long contentId) {
        return contentRepository.findById(contentId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.error("Content not found for mockup failure: {}", contentId);
                    return Mono.error(ApiException.of(ErrorCode.NOT_FOUND,
                            "Content not found for mockup failure: " + contentId));
                }))
                .flatMap(content -> {
                    content.updateMockupFailed();
                    return contentRepository.save(content);
                });
    }

    // ============================
    // ✅ Job 결과 URL 업데이트 (SNS/VIDEO/BANNER/DIELINE 등)
    // ============================

    /**
     * SNS/VIDEO/DIELINE 등 결과물 URL -> gcs_img_url 저장
     * (DB에는 gs:// 또는 bucket/key 형태를 저장하고, 응답에서 toPublicUrl로 http 변환)
     */
    public Mono<Void> applyMediaResult(Long contentId, String gcsUrl) {
        return contentRepository.findById(contentId)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.NOT_FOUND,
                        "콘텐츠를 찾을 수 없습니다. id=" + contentId)))
                .flatMap(content -> {
                    // ⚠️ 아래 메서드명이 실제 엔티티에 있어야 함.
                    // 없다면 content.updateMockup(...) 같은 잘못된 메서드 쓰지 말고 엔티티에 updateGcsImgUrl 추가해라.
                    content.updateGcsImgUrl(gcsUrl);
                    return contentRepository.save(content);
                })
                .then();
    }

    /**
     * 배너 결과물: gcs_img_url + bannerRatio 저장
     */
    public Mono<Void> applyBannerResult(Long contentId, String gcsUrl, Integer bannerRatio) {
        return contentRepository.findById(contentId)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.NOT_FOUND,
                        "콘텐츠를 찾을 수 없습니다. id=" + contentId)))
                .flatMap(content -> {
                    content.updateGcsImgUrl(gcsUrl);
                    if (bannerRatio != null) {
                        content.updateBannerRatio(bannerRatio);
                    }
                    return contentRepository.save(content);
                })
                .then();
    }

    /**
     * 실패를 content.status로 박고 싶으면 엔티티/enum 확정 후 처리.
     * 지금은 최소한 로그는 남겨라. (Mono.empty()는 실패를 조용히 숨김)
     */
    public Mono<Void> markContentFailed(Long contentId) {
        log.warn("markContentFailed called. contentId={}", contentId);
        return Mono.empty();

        /*
        return contentRepository.findById(contentId)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.NOT_FOUND,
                        "콘텐츠를 찾을 수 없습니다. id=" + contentId)))
                .flatMap(content -> {
                    content.markGenerationFailed();
                    return contentRepository.save(content);
                })
                .then();
        */
    }

    // ============================
    // (호환용) 기존 Job 결과 업데이트 메서드
    // ============================

    /**
     * 기존 호출부 호환용.
     * 예전 코드에서 updateUrlFromJob(contentId, url) 호출하던 곳은
     * 이제 applyMediaResult로 통일하는 게 맞다.
     */
    public Mono<Content> updateUrlFromJob(Long contentId, String generatedImgUrl) {
        return contentRepository.findById(contentId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.error("Content not found for job result: {}", contentId);
                    return Mono.error(ApiException.of(ErrorCode.NOT_FOUND,
                            "Content not found for job result: " + contentId));
                }))
                .flatMap(content -> {
                    content.updateGcsImgUrl(generatedImgUrl);
                    return contentRepository.save(content);
                });
    }

    // ============================
    // ContentAsset 조회/삭제
    // ============================

    public Flux<ContentAssetResponse> getAssetsByContent(Long contentId) {
        return contentAssetRepository.findByContentIdOrderBySortOrderAsc(contentId)
                .map(this::toAssetResponse);
    }

    public Mono<Void> deleteAsset(Long assetId) {
        return contentAssetRepository.findById(assetId)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.NOT_FOUND, "에셋을 찾을 수 없습니다.")))
                .flatMap(contentAssetRepository::delete);
    }

    // ============================
    // Base Image 조회 (for Package Mockup)
    // ============================

    public Flux<com.example.chillgram.domain.product.dto.BaseImageResponse> getBaseImagesByProduct(Long productId) {
        return contentRepository.findBaseImagesByProductId(productId)
                .map(content -> new com.example.chillgram.domain.product.dto.BaseImageResponse(
                        gcs.toPublicUrl(content.getGcsImgUrl()),
                        content.getContentType(),
                        content.getProjectId(),
                        content.getUpdatedAt()
                ));
    }

    // ============================
    // Mapper (응답은 항상 http URL로 변환)
    // ============================

    private ContentResponse toResponse(Content c) {
        return new ContentResponse(
                c.getId(), c.getCompanyId(), c.getProductId(), c.getProjectId(),
                c.getContentType(), c.getPlatform(),
                c.getTitle(), c.getBody(), c.getStatus(), c.getTags(),
                c.getViewCount(), c.getLikeCount(), c.getShareCount(),
                c.getBannerRatio(),
                gcs.toPublicUrl(c.getGcsImgUrl()),
                gcs.toPublicUrl(c.getMockupImgUrl()),
                c.getCreatedAt(), c.getUpdatedAt()
        );
    }

    private ContentAssetResponse toAssetResponse(ContentAsset a) {
        return new ContentAssetResponse(
                a.getId(), a.getContentId(), a.getAssetType(),
                gcs.toPublicUrl(a.getFileUrl()),
                gcs.toPublicUrl(a.getThumbUrl()),
                a.getMimeType(),
                a.getFileSize(), a.getWidth(), a.getHeight(),
                a.getDurationMs(), a.getSortOrder(), a.getCreatedAt()
        );
    }
}
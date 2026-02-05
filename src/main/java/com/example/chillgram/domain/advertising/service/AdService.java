package com.example.chillgram.domain.advertising.service;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.domain.advertising.dto.*;
import com.example.chillgram.domain.advertising.engine.TrendRuleEngine;
import com.example.chillgram.domain.advertising.repository.AdCreateRepository;
import com.example.chillgram.domain.advertising.repository.EventCalendarRepository;
import com.example.chillgram.domain.ai.dto.*;
import com.example.chillgram.domain.ai.service.AdCopyService;
import com.example.chillgram.domain.product.entity.Product;
import com.example.chillgram.domain.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.List;

@Service
public class AdService {

    private final ProductRepository productRepository;
    private final EventCalendarRepository eventCalendarRepository;
    private final AdCreateRepository adCreateRepository;
    private final TrendRuleEngine trendEngine;
    private final AdCopyService adCopyService;
    private final TransactionalOperator tx;
    private final WebClient aiClient;

    public AdService(
            ProductRepository productRepository,
            EventCalendarRepository eventCalendarRepository,
            AdCreateRepository adCreateRepository,
            TrendRuleEngine trendEngine,
            AdCopyService adCopyService,
            TransactionalOperator tx,
            WebClient aiClient
    ) {
        this.productRepository = productRepository;
        this.eventCalendarRepository = eventCalendarRepository;
        this.adCreateRepository = adCreateRepository;
        this.trendEngine = trendEngine;
        this.adCopyService = adCopyService;
        this.tx = tx;
        this.aiClient = aiClient;
    }

    private Mono<Product> requireProduct(long productId) {
        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(ApiException.of(
                        ErrorCode.AD_PRODUCT_NOT_FOUND, "product not found id=" + productId)));
    }

    public Mono<AdTrendsResponse> getAdTrends(long productId, LocalDate baseDate) {
        final LocalDate date = (baseDate != null) ? baseDate : LocalDate.now();

        Mono<Void> ensureProductExists = productRepository.existsById(productId)
                .flatMap(exists -> exists
                        ? Mono.empty()
                        : Mono.error(ApiException.of(
                        ErrorCode.AD_PRODUCT_NOT_FOUND, "product not found id=" + productId)));

        return ensureProductExists.then(
                eventCalendarRepository.findNearest(date, 5)
                        .collectList()
                        .map(events -> {
                            var r = trendEngine.analyze(productId, date, events);

                            var dtoKeywords = r.trendKeywords().stream()
                                    .map(k -> new AdTrendsResponse.TrendKeyword(k.name(), k.description()))
                                    .toList();

                            return new AdTrendsResponse(
                                    productId,
                                    date,
                                    dtoKeywords,
                                    r.hashtags(),
                                    r.styleSummary()
                            );
                        })
        );
    }

    public Mono<AdGuidesResponse> createAdGuides(long productId, AdGuidesRequest req) {
        final LocalDate date = (req.baseDate() != null) ? req.baseDate() : LocalDate.now();

        Mono<Product> productMono = requireProduct(productId);
        Mono<AdTrendsResponse> trendsMono = getAdTrends(productId, date);

        return Mono.zip(productMono, trendsMono)
                .flatMap(tuple -> {
                    Product product = tuple.getT1();
                    AdTrendsResponse trends = tuple.getT2();

                    AdGuideAiRequest aiReq = AdGuideAiRequest.from(productId, product, date, req, trends);

                    return adCopyService.generateAdGuidesMono(aiReq)
                            .map(aiResp -> toResponse(productId, product, date, req, aiResp));
                })
                .onErrorMap(ex -> (ex instanceof ApiException) ? ex
                        : ApiException.of(ErrorCode.AD_GUIDE_GENERATION_FAILED, ex.getMessage()));
    }

    private AdGuidesResponse toResponse(
            long productId,
            Product product,
            LocalDate date,
            AdGuidesRequest req,
            AdGuideAiResponse aiResp
    ) {
        List<AdGuidesResponse.GuideSection> sections = aiResp.sections().stream()
                .map(s -> new AdGuidesResponse.GuideSection(s.section(), s.content()))
                .toList();

        return new AdGuidesResponse(
                productId,
                product.getName(),
                date,
                req.title(),
                req.adGoal(),
                req.requestText(),
                req.selectedKeywords() == null ? List.of() : req.selectedKeywords(),
                req.adFocus(),
                sections
        );
    }

    public Mono<FinalCopyResponse> createAdCopies(long productId, AdCopiesRequest req) {
        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(
                        ApiException.of(ErrorCode.AD_PRODUCT_NOT_FOUND, "product not found id=" + productId)
                ))
                .flatMap(product ->
                        Mono.fromCallable(() ->
                                adCopyService.generateFinalCopy(
                                        new FinalCopyRequest(
                                                product.getName(),
                                                req.keyword(),
                                                req.selectedConcept(),
                                                req.selectedDescription(),
                                                req.tone()
                                        )
                                )
                        ).subscribeOn(Schedulers.boundedElastic())
                )
                .onErrorMap(ex ->
                        (ex instanceof ApiException) ? ex
                                : ApiException.of(ErrorCode.AD_COPY_GENERATION_FAILED, ex.getMessage())
                );
    }

    public Mono<AdCreateResponse> createProjectAndContents(long productId, AdCreateRequest req) {
        // 최소 검증 (안 하면 DB에서 더 더럽게 터짐)
        if (req.projectTitle() == null || req.projectTitle().trim().isEmpty()) {
            return Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED, "projectTitle is required"));
        }
        if (req.selectedTypes() == null || req.selectedTypes().isEmpty()) {
            return Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED, "selectedTypes is required"));
        }
        if (req.selectedCopy() == null || req.selectedCopy().finalCopy() == null || req.selectedCopy().finalCopy().isBlank()) {
            return Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED, "selectedCopy.finalCopy is required"));
        }

        // product 존재 검증
        Mono<Void> ensureProduct = productRepository.existsById(productId)
                .flatMap(exists -> exists
                        ? Mono.empty()
                        : Mono.error(ApiException.of(ErrorCode.AD_PRODUCT_NOT_FOUND, "product not found id=" + productId)));

        // company_id 조회 (FK 때문에 필수)
        Mono<Long> companyIdMono = adCreateRepository.findCompanyIdByProductId(productId)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.AD_PRODUCT_NOT_FOUND, "company not found by productId=" + productId)));

        return ensureProduct.then(companyIdMono)
                .flatMap(companyId ->
                        adCreateRepository.insertProject(
                                        companyId,
                                        productId,
                                        safe(req.projectTitle()),
                                        safe(req.requestText())
                                )
                                .flatMap(projectId ->
                                        insertContents(companyId, productId, projectId, req)
                                                .collectList()
                                                .map(contentIds -> new AdCreateResponse(projectId, contentIds))
                                )
                )
                .as(tx::transactional)
                .onErrorMap(ex -> (ex instanceof ApiException) ? ex
                        : ApiException.of(ErrorCode.INTERNAL_ERROR, ex.getMessage()));
    }

    private Flux<Long> insertContents(long companyId, long productId, long projectId, AdCreateRequest req) {
        String tags = buildTags(req.selectedKeywords());
        String body = buildBody(req);

        return Flux.fromIterable(req.selectedTypes())
                .map(ContentMeta::fromUiLabel) // UI → DB enum
                .flatMap(meta ->
                        adCreateRepository.insertContent(
                                companyId,
                                productId,
                                projectId,
                                meta.contentType(),
                                meta.platform(),
                                meta.makeTitle(req.projectTitle()),
                                body,
                                tags
                        )
                );
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String buildTags(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return null;
        return String.join(",", keywords.stream().map(String::trim).filter(k -> !k.isBlank()).toList());
    }

    private static String buildBody(AdCreateRequest req) {
        FinalCopyResponse copy = req.selectedCopy();

        StringBuilder sb = new StringBuilder();
        sb.append("## STEP1\n");
        sb.append("- adGoal: ").append(safe(req.adGoal())).append("\n");
        sb.append("- requestText: ").append(safe(req.requestText())).append("\n");
        sb.append("- keywords: ").append(req.selectedKeywords()).append("\n");
        sb.append("- adFocus: ").append(req.adFocus()).append("\n\n");

        sb.append("## STEP2\n");
        sb.append("- guideId: ").append(safe(req.selectedGuideId())).append("\n\n");

        sb.append("## STEP3\n");
        sb.append("- concept: ").append(safe(copy.selectedConcept())).append("\n");
        sb.append("- finalCopy:\n").append(safe(copy.finalCopy())).append("\n\n");

        if (copy.shortformPrompt() != null && !copy.shortformPrompt().isBlank()) {
            sb.append("## SHORTFORM_PROMPT\n").append(copy.shortformPrompt()).append("\n\n");
        }
        if (copy.bannerPrompt() != null && !copy.bannerPrompt().isBlank()) {
            sb.append("## BANNER_PROMPT\n").append(copy.bannerPrompt()).append("\n\n");
        }
        if (copy.snsPrompt() != null && !copy.snsPrompt().isBlank()) {
            sb.append("## SNS_PROMPT\n").append(copy.snsPrompt()).append("\n\n");
        }
        if (copy.selectionReason() != null && !copy.selectionReason().isBlank()) {
            sb.append("## SELECTION_REASON\n").append(copy.selectionReason()).append("\n");
        }
        return sb.toString();
    }

    /**
     * content_type: IMAGE/VIDEO/TEXT
     * platform: INSTAGRAM/FACEBOOK/YOUTUBE
     */
    private record ContentMeta(String contentType, String platform, String kind) {
        static ContentMeta fromUiLabel(String label) {
            String v = label == null ? "" : label.trim();
            return switch (v) {
                case "패키지 시안 AI" -> new ContentMeta("IMAGE", "INSTAGRAM", "PACKAGE");
                case "SNS 이미지 AI" -> new ContentMeta("IMAGE", "INSTAGRAM", "SNS_IMAGE");
                case "배너 이미지 AI" -> new ContentMeta("IMAGE", "FACEBOOK", "BANNER");
                case "숏츠 AI" -> new ContentMeta("VIDEO", "YOUTUBE", "SHORTS");
                default -> new ContentMeta("TEXT", "INSTAGRAM", "TEXT");
            };
        }

        String makeTitle(String baseTitle) {
            String t = safe(baseTitle);
            return switch (kind) {
                case "PACKAGE" -> t + " · 패키지 시안";
                case "SNS_IMAGE" -> t + " · SNS 이미지";
                case "BANNER" -> t + " · 배너 이미지";
                case "SHORTS" -> t + " · 숏츠";
                default -> t + " · 텍스트";
            };
        }
    }
}

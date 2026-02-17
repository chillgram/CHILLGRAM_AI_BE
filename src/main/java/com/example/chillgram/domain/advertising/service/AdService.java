package com.example.chillgram.domain.advertising.service;

import lombok.extern.slf4j.Slf4j;
import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.common.google.FileStorage;
import com.example.chillgram.domain.advertising.dto.*;
import com.example.chillgram.domain.advertising.dto.jobs.CreateJobRequest;
import com.example.chillgram.domain.advertising.dto.jobs.JobEnums;
import com.example.chillgram.domain.advertising.engine.TrendRuleEngine;
import com.example.chillgram.domain.advertising.repository.AdCreateRepository;
import com.example.chillgram.domain.advertising.repository.AdGenLogRepository;
import com.example.chillgram.domain.advertising.repository.EventCalendarRepository;
import com.example.chillgram.domain.ai.dto.*;
import com.example.chillgram.domain.ai.service.AdCopyService;
import com.example.chillgram.domain.ai.service.JobService;
import com.example.chillgram.domain.product.entity.Product;
import com.example.chillgram.domain.product.repository.ProductRepository;
import com.example.chillgram.domain.project.repository.ProjectRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.List;

@Service
@Slf4j
public class AdService {

        private final ProductRepository productRepository;
        private final ProjectRepository projectRepository;
        private final EventCalendarRepository eventCalendarRepository;
        private final AdCreateRepository adCreateRepository;
        private final TrendRuleEngine trendEngine;
        private final AdCopyService adCopyService;
        private final TransactionalOperator tx;
        private final JobService jobService;
        private final ObjectMapper objectMapper;

        private final AdGenLogRepository adGenLogRepository;

        public AdService(
                        ProductRepository productRepository,
                        ProjectRepository projectRepository,
                        EventCalendarRepository eventCalendarRepository,
                        AdCreateRepository adCreateRepository,
                        TrendRuleEngine trendEngine,
                        AdCopyService adCopyService,
                        TransactionalOperator tx,
                        JobService jobService,
                        ObjectMapper objectMapper,
                        AdGenLogRepository adGenLogRepository
        ) {
                this.productRepository = productRepository;
                this.projectRepository = projectRepository;
                this.eventCalendarRepository = eventCalendarRepository;
                this.adCreateRepository = adCreateRepository;
                this.trendEngine = trendEngine;
                this.adCopyService = adCopyService;
                this.tx = tx;
                this.jobService = jobService;
                this.objectMapper = objectMapper;
                this.adGenLogRepository = adGenLogRepository;
                log.info("AdService initialized");
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
                                                                ErrorCode.AD_PRODUCT_NOT_FOUND,
                                                                "product not found id=" + productId)));

                return ensureProductExists.then(
                                eventCalendarRepository.findNearest(date, 5)
                                                .collectList()
                                                .map(events -> {
                                                        var r = trendEngine.analyze(productId, date, events);

                                                        var dtoKeywords = r.trendKeywords().stream()
                                                                        .map(k -> new AdTrendsResponse.TrendKeyword(
                                                                                        k.name(), k.description()))
                                                                        .toList();

                                                        return new AdTrendsResponse(
                                                                        productId,
                                                                        date,
                                                                        dtoKeywords,
                                                                        r.hashtags(),
                                                                        r.styleSummary());
                                                }));
        }

        public Mono<AdGuidesResponse> createAdGuides(long productId, AdGuidesRequest req) {
                final LocalDate date = (req.baseDate() != null) ? req.baseDate() : LocalDate.now();

                Mono<Product> productMono = requireProduct(productId);
                Mono<AdTrendsResponse> trendsMono = getAdTrends(productId, date);

                return Mono.zip(productMono, trendsMono)
                                .flatMap(tuple -> {
                                        Product product = tuple.getT1();
                                        AdTrendsResponse trends = tuple.getT2();

                                        AdGuideAiRequest aiReq = AdGuideAiRequest.from(productId, product, date, req,
                                                        trends);

                                        return adCopyService.generateAdGuidesMono(aiReq);
                                })
                                .onErrorMap(ex -> (ex instanceof ApiException) ? ex
                                                : ApiException.of(ErrorCode.AD_GUIDE_GENERATION_FAILED,
                                                                ex.getMessage()));
        }

        public Mono<FinalCopyResponse> createAdCopies(long productId, FinalCopyRequest req) {
                if (req.selectedGuideline() == null || req.selectedGuideline().isEmpty()) {
                        return Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED,
                                        "selectedGuideline is required"));
                }
                return productRepository.findById(productId)
                                .switchIfEmpty(Mono.error(
                                                ApiException.of(ErrorCode.AD_PRODUCT_NOT_FOUND,
                                                                "product not found id=" + productId)))
                                .flatMap(product -> Mono.fromCallable(() -> {
                                        return adCopyService.generateFinalCopies(req);
                                })
                                                .subscribeOn(Schedulers.boundedElastic()))
                                .onErrorMap(ex -> (ex instanceof ApiException) ? ex
                                                : ApiException.of(ErrorCode.AD_COPY_GENERATION_FAILED,
                                                                ex.getMessage()));
        }

        public Mono<AdCreateResponse> createProjectAndContents(
                        long productId,
                        AdCreateRequest req,
                        FileStorage.StoredFile stored,
                        long userId) {
                if (stored == null || stored.fileUrl() == null)
                        return Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED, "file required"));

                return productRepository.existsById(productId)
                                .flatMap(exists -> exists ? Mono.empty()
                                                : Mono.error(ApiException.of(ErrorCode.AD_PRODUCT_NOT_FOUND,
                                                                "product not found")))
                                .then(adCreateRepository.findCompanyIdByProductId(productId))
                                .flatMap(companyId -> adCreateRepository.insertProject(
                                                companyId,
                                                productId,
                                                req.projectType(),
                                                req.projectTitle(),
                                                req.requestText(),
                                                userId,
                                                req.adFocus(),
                                                req.adMessageTarget(),
                                                stored.fileUrl())
                                                .flatMap(projectId -> insertContents(companyId, productId, projectId,
                                                                req, stored, userId)
                                                                .collectList()
                                                                .flatMap(contentIds -> {
                                                                        return Mono.just(new AdCreateResponse(projectId,
                                                                                        contentIds, null));
                                                                })))
                                .as(tx::transactional);
        }

        private Flux<Long> insertContents(
                        long companyId,
                        long productId,
                        long projectId,
                        AdCreateRequest req,
                        FileStorage.StoredFile stored,
                        long userId) {
                int bannerRatio = 0;
                if (req.bannerSize() != null) {
                        if (req.bannerSize().contains("1:1"))
                                bannerRatio = 1;
                        else if (req.bannerSize().contains("16:9"))
                                bannerRatio = 2;
                        else if (req.bannerSize().contains("9:16"))
                                bannerRatio = 3;
                }

                Integer finalBannerRatio = bannerRatio;

                return Flux.fromIterable(req.selectedTypes())
                                .flatMap(type -> {
                                        String contentType = "IMAGE";
                                        String platform = "INSTAGRAM";

                                        if (type != null) {
                                                String upper = type.toUpperCase();
                                                if (upper.contains("VIDEO") || upper.contains("SHORT")) {
                                                        contentType = "VIDEO";
                                                        platform = "YOUTUBE";
                                                }
                                        }

                                        return adCreateRepository.insertContent(
                                                        companyId,
                                                        productId,
                                                        projectId,
                                                        contentType,
                                                        platform,
                                                        req.projectTitle(),
                                                        req.selectedCopy() != null ? req.selectedCopy().body()
                                                                        : "",
                                                        req.selectedKeywords() != null
                                                                        && !req.selectedKeywords().isEmpty()
                                                                                        ? String.join(",", req
                                                                                                        .selectedKeywords())
                                                                                        : null,
                                                        userId,
                                                        finalBannerRatio)
                                                        .flatMap(contentId -> adCreateRepository.insertContentAsset(
                                                                        contentId,
                                                                        stored.fileUrl(),
                                                                        stored.mimeType(),
                                                                        stored.fileSize())
                                                                        .thenReturn(contentId));
                                });
        }

        public Mono<Long> saveAdGenerationLog(long productId, AdGenLogRequest req, long userId) {
                return productRepository.existsById(productId)
                                .flatMap(exists -> exists ? Mono.empty()
                                                : Mono.error(ApiException.of(ErrorCode.AD_PRODUCT_NOT_FOUND,
                                                                "product not found")))
                                .then(adCreateRepository.findCompanyIdByProductId(productId))
                                .flatMap(companyId -> {
                                        String finalCopyJson = "{}";
                                        String guidelineJson = "{}";
                                        try {
                                                finalCopyJson = objectMapper.writeValueAsString(req.finalCopy());
                                                guidelineJson = objectMapper.writeValueAsString(req.guideline());
                                        } catch (JsonProcessingException e) {
                                                finalCopyJson = String.valueOf(req.finalCopy());
                                                guidelineJson = String.valueOf(req.guideline());
                                        }

                                        return adGenLogRepository.save(
                                                        companyId,
                                                        userId,
                                                        productId,
                                                        finalCopyJson,
                                                        guidelineJson,
                                                        req.selectionReason());
                                });
        }

        public Mono<AdGuideResponse> generateAdGuides(Long projectId, AdGuideRequest request, Long companyId) {
                return projectRepository.findById(projectId)
                                .filter(p -> p.getCompanyId().equals(companyId))
                                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.PROJECT_NOT_FOUND,
                                                "Project not found: " + projectId)))
                                .flatMap(project -> productRepository.findById(project.getProductId())
                                                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.AD_PRODUCT_NOT_FOUND,
                                                                "Product not found")))
                                                .flatMap(product -> {
                                                        AdGuideAiRequest aiReq = AdGuideAiRequest.of(project, product,
                                                                        request);
                                                        return adCopyService.generateVisualGuidesMono(aiReq)
                                                                        .map(options -> new AdGuideResponse(projectId,
                                                                                        options));
                                                }))
                                .onErrorMap(ex -> (ex instanceof ApiException) ? ex
                                                : ApiException.of(ErrorCode.AD_GUIDE_GENERATION_FAILED,
                                                                ex.getMessage()));
        }

        public Mono<List<String>> generateCopyVariations(Long projectId, CopyVariationRequest request, Long companyId) {
                return projectRepository.findById(projectId)
                                .filter(p -> p.getCompanyId().equals(companyId))
                                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.PROJECT_NOT_FOUND,
                                                "Project not found: " + projectId)))
                                .flatMap(project -> adCopyService.generateCopyVariationsMono(request.selectedOption(),
                                                project.getAdMessageTarget()))
                                .onErrorMap(ex -> (ex instanceof ApiException) ? ex
                                                : ApiException.of(ErrorCode.AD_COPY_GENERATION_FAILED,
                                                                ex.getMessage()));
        }
}

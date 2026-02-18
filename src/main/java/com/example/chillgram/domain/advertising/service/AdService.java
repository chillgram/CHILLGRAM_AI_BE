package com.example.chillgram.domain.advertising.service;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
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
import lombok.extern.slf4j.Slf4j;
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
                        AdGenLogRepository adGenLogRepository) {
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
                                .flatMap(product -> Mono.fromCallable(() -> adCopyService.generateFinalCopies(req))
                                                .subscribeOn(Schedulers.boundedElastic()))
                                .onErrorMap(ex -> (ex instanceof ApiException) ? ex
                                                : ApiException.of(ErrorCode.AD_COPY_GENERATION_FAILED,
                                                                ex.getMessage()));
        }

    public Mono<AdCreateResponse> createProjectAndContents(long productId, long userId, AdCreateRequest req) {

        return productRepository.existsById(productId)
                .flatMap(exists -> exists
                        ? Mono.empty()
                        : Mono.error(ApiException.of(ErrorCode.AD_PRODUCT_NOT_FOUND, "product not found")))
                .then(adCreateRepository.findCompanyIdByProductId(productId))
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.AD_PRODUCT_NOT_FOUND, "company not found by productId")))
                .flatMap(companyId -> {
                    String userImgUrl = req.selectedProductImage() != null ? req.selectedProductImage().url() : null;

                    return adCreateRepository.insertProject(
                                    companyId,
                                    productId,
                                    req.projectType(),
                                    req.projectTitle(),
                                    req.requestText(),
                                    userId,
                                    normalizeFocus(req.adFocus()),
                                    req.adMessageTarget(),
                                    userImgUrl
                            )
                            .flatMap(projectId ->
                                    insertContents(companyId, productId, projectId, req, userId)
                                            .collectList()
                                            .map(contentIds -> new AdCreateResponse(projectId, contentIds, null))
                            );
                })
                .flatMap(resp ->
                        productRepository.findCategoryByProductId(productId)
                                .defaultIfEmpty("0")
                                .flatMap(cat -> {
                                    AdCreateResponse respWithCat = new AdCreateResponse(resp.projectId(), resp.contentIds(), cat);

                                    return publishJobs(productId, userId, req, respWithCat)
                                            .thenReturn(respWithCat);
                                })
                )
                .as(tx::transactional);
    }

        /**
         * content 개수만큼 jobService.requestJob 호출(= outbox 발행)
         * - contentIds와 selectedTypes를 같은 index로 매칭
         * - ✅ BANNER인 경우 payload에 bannerRatio(idx) 포함
         */
        private Mono<Void> publishJobs(long productId, long userId, AdCreateRequest req, AdCreateResponse resp) {

                List<Long> contentIds = resp.contentIds();
                List<String> selectedTypes = req.selectedTypes();

                int n = Math.min(contentIds.size(), selectedTypes.size());
                if (n == 0)
                        return Mono.empty();

                final int bannerRatio = bannerRatioIdx(req.bannerSize());

                return Flux.range(0, n)
                                .concatMap(i -> {
                                        long contentId = contentIds.get(i);
                                        String type = selectedTypes.get(i);

                                        JobEnums.JobType jobType = mapToJobType(type);

                                        ObjectNode payload = objectMapper.createObjectNode();
                                        payload.put("productId", productId);
                                        payload.put("projectId", resp.projectId());
                                        payload.put("contentId", contentId);
                                        payload.put("productName", req.productName());
                                        payload.put("guideLine", req.selectedGuide());
                                        payload.put("typoText", req.selectedCopy().body() == null ? "" : req.selectedCopy().body());
                                        payload.put("baseImageUrl", req.selectedProductImage() != null ? req.selectedProductImage().url() : "");
                                        payload.put("bannerSize", req.bannerSize() == null ? "" : req.bannerSize());
                                        payload.put("adMessageTarget", req.adMessageTarget() == null ? 0 : req.adMessageTarget());
                                        payload.put("category", resp.category());

                                        if (jobType == JobEnums.JobType.BANNER) {
                                            payload.put("bannerRatio", bannerRatio);
                                        }
                                        // ✅ 핵심: 배너만 ratio idx를 큐 payload에 실어야 워커가 그대로 사용 가능
                                        if (jobType == JobEnums.JobType.BANNER) {
                                                payload.put("bannerRatio", bannerRatio); // 1~10, 없으면 0
                                        }

                                        CreateJobRequest jobReq = new CreateJobRequest(jobType, payload);

                                        return jobService.requestJob(resp.projectId(), jobReq, null).then();
                                })
                                .then();
        }

        private JobEnums.JobType mapToJobType(String type) {
                if (type == null || type.isBlank())
                        return JobEnums.JobType.SNS;
                try {
                        return JobEnums.JobType.valueOf(type.trim().toUpperCase());
                } catch (Exception e) {
                        return JobEnums.JobType.SNS;
                }
        }

        /**
         * 프론트 BANNER_RATIOS value -> idx 매핑
         */
        private int bannerRatioIdx(String bannerSize) {
                if (bannerSize == null)
                        return 0;
                return switch (bannerSize) {
                        case "1:1" -> 1;
                        case "2:3" -> 2;
                        case "3:2" -> 3;
                        case "3:4" -> 4;
                        case "4:3" -> 5;
                        case "4:5" -> 6;
                        case "5:4" -> 7;
                        case "9:16" -> 8;
                        case "16:9" -> 9;
                        case "21:9" -> 10;
                        default -> 0;
                };
        }

        private Flux<Long> insertContents(long companyId, long productId, long projectId, AdCreateRequest req,
                        long userId) {

                final int finalBannerRatio = bannerRatioIdx(req.bannerSize());

                return Flux.fromIterable(req.selectedTypes())
                                .concatMap(type -> {

                                        JobEnums.JobType jobType = mapToJobType(type);
                                        String contentType = jobType.name();
                                        String platform = switch (jobType) {
                                                case VIDEO -> "YOUTUBE";
                                                case SNS, BANNER -> "INSTAGRAM";
                                                default -> "INSTAGRAM";
                                        };

                                        // 배너만 ratio 저장, 나머지는 0
                                        Integer ratioToSave = (jobType == JobEnums.JobType.BANNER) ? finalBannerRatio
                                                        : 0;

                                        return adCreateRepository.insertContent(
                                                        companyId,
                                                        productId,
                                                        projectId,
                                                        contentType,
                                                        platform,
                                                        req.projectTitle(),
                                                        req.selectedCopy() != null ? req.selectedCopy().body() : "",
                                                        req.selectedKeywords() != null
                                                                        && !req.selectedKeywords().isEmpty()
                                                                                        ? String.join(",", req
                                                                                                        .selectedKeywords())
                                                                                        : null,
                                                        userId,
                                                        ratioToSave);
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
                                                .switchIfEmpty(Mono
                                                                .error(ApiException.of(ErrorCode.AD_PRODUCT_NOT_FOUND,
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
                                .flatMap(project -> adCopyService.generateCopyVariationsMono(
                                                request.selectedOption(),
                                                project.getAdMessageTarget()))
                                .onErrorMap(ex -> (ex instanceof ApiException) ? ex
                                                : ApiException.of(ErrorCode.AD_COPY_GENERATION_FAILED,
                                                                ex.getMessage()));

        }

        // 임시 무결설 체크
        private int normalizeFocus(int v) {
                if (0 <= v && v <= 4)
                        return v;
                if (0 <= v && v <= 100)
                        return Math.min(4, Math.max(0, Math.round(v / 25f)));
                return 0;
        }
}

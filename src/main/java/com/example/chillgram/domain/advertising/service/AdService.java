package com.example.chillgram.domain.advertising.service;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.domain.advertising.dto.*;
import com.example.chillgram.domain.advertising.dto.jobs.CreateJobRequest;
import com.example.chillgram.domain.advertising.dto.jobs.JobEnums;
import com.example.chillgram.domain.advertising.engine.TrendRuleEngine;
import com.example.chillgram.domain.advertising.repository.AdCreateRepository;
import com.example.chillgram.domain.advertising.repository.EventCalendarRepository;
import com.example.chillgram.domain.ai.dto.*;
import com.example.chillgram.domain.ai.service.AdCopyService;
import com.example.chillgram.domain.ai.service.JobService;
import com.example.chillgram.domain.product.entity.Product;
import com.example.chillgram.domain.product.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
        private final JobService jobService;
        private final ObjectMapper objectMapper;

        public AdService(
                        ProductRepository productRepository,
                        EventCalendarRepository eventCalendarRepository,
                        AdCreateRepository adCreateRepository,
                        TrendRuleEngine trendEngine,
                        AdCopyService adCopyService,
                        TransactionalOperator tx,
                        JobService jobService,
                        ObjectMapper objectMapper) {
                this.productRepository = productRepository;
                this.eventCalendarRepository = eventCalendarRepository;
                this.adCreateRepository = adCreateRepository;
                this.trendEngine = trendEngine;
                this.adCopyService = adCopyService;
                this.tx = tx;
                this.jobService = jobService;
                this.objectMapper = objectMapper;
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

                                        return adCopyService.generateAdGuidesMono(aiReq)
                                                        .map(aiResp -> toResponse(productId, product, date, req,
                                                                        aiResp));
                                })
                                .onErrorMap(ex -> (ex instanceof ApiException) ? ex
                                                : ApiException.of(ErrorCode.AD_GUIDE_GENERATION_FAILED,
                                                                ex.getMessage()));
        }

        private AdGuidesResponse toResponse(
                        long productId,
                        Product product,
                        LocalDate date,
                        AdGuidesRequest req,
                        AdGuideAiResponse aiResp) {
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
                                sections);
        }

        public Mono<FinalCopyResponse> createAdCopies(long productId, AdCopiesRequest req) {
                return productRepository.findById(productId)
                                .switchIfEmpty(Mono.error(
                                                ApiException.of(ErrorCode.AD_PRODUCT_NOT_FOUND,
                                                                "product not found id=" + productId)))
                                .flatMap(product -> Mono.fromCallable(() -> adCopyService.generateFinalCopy(
                                                new FinalCopyRequest(
                                                                product.getName(),
                                                                req.keyword(),
                                                                req.selectedConcept(),
                                                                req.selectedDescription(),
                                                                req.tone())))
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

                                                                        // Step 4, 5 생략 (사용자 요청)
                                                                        // CreateJobRequest jobReq =
                                                                        // buildJobPayload(req, projectId, contentIds,
                                                                        // stored.fileUrl());
                                                                        // return jobService.requestJob(projectId,
                                                                        // jobReq, "").map(jobId -> new
                                                                        // AdCreateResponse(projectId, contentIds,
                                                                        // jobId));

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
                // 배너 비율 매핑
                // 1:1 -> 1, 16:9 -> 2, 9:16 -> 3, others -> 0
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
                                        // 타입에 따른 분기 처리
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
                                                        req.selectedCopy() != null ? req.selectedCopy().finalCopy()
                                                                        : "",
                                                        null,
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

        private CreateJobRequest buildJobPayload(
                        AdCreateRequest req,
                        long projectId,
                        List<Long> contentIds,
                        String imageUrl) {
                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("projectId", projectId);
                payload.put("imageUrl", imageUrl);
                payload.put("finalCopy", req.selectedCopy().finalCopy());

                var arr = payload.putArray("contentIds");
                contentIds.forEach(arr::add);

                return new CreateJobRequest(
                                JobEnums.JobType.BANNER,
                                payload);
        }
}

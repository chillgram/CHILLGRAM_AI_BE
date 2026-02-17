package com.example.chillgram.domain.product.service;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.common.google.FileStorage.StoredFile;
import com.example.chillgram.common.google.GcsFileStorage;
import com.example.chillgram.domain.advertising.dto.jobs.CreateJobRequest;
import com.example.chillgram.domain.advertising.dto.jobs.JobEnums.JobType;
import com.example.chillgram.domain.ai.service.JobService;
import com.example.chillgram.domain.company.domain.Company;
import com.example.chillgram.domain.company.repository.CompanyRepository;
import com.example.chillgram.domain.content.entity.Content;
import com.example.chillgram.domain.content.repository.ContentRepository;
import com.example.chillgram.domain.content.repository.ContentAssetRepository;
import com.example.chillgram.domain.product.dto.ProductCreateRequest;
import com.example.chillgram.domain.product.dto.ProductDashboardStats;
import com.example.chillgram.domain.product.dto.ProductResponse;
import com.example.chillgram.domain.product.dto.ProductUpdateRequest;
import com.example.chillgram.domain.product.entity.Product;
import com.example.chillgram.domain.product.repository.ProductRepository;
import com.example.chillgram.domain.project.repository.ProjectRepository;
import com.example.chillgram.common.security.AuthPrincipal;
import com.example.chillgram.domain.user.domain.AppUser;
import com.example.chillgram.domain.user.repository.AppUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.common.util.StringUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional(readOnly = true)
public class ProductService {

        private final ProductRepository productRepository;
        private final CompanyRepository companyRepository;
        private final AppUserRepository appUserRepository;
        private final JobService jobService;
        private final GcsFileStorage gcs;
        private final ContentRepository contentRepo;
        private final ContentAssetRepository contentAssetRepository;
        private final ProjectRepository projectRepository;
        private final TransactionalOperator tx;
        private final ObjectMapper om;

        public ProductService(
                        ProductRepository productRepository,
                        CompanyRepository companyRepository,
                        AppUserRepository appUserRepository,
                        JobService jobService,
                        GcsFileStorage gcs,
                        ContentRepository contentRepo,
                        ContentAssetRepository contentAssetRepository,
                        ProjectRepository projectRepository,
                        TransactionalOperator tx,
                        ObjectMapper om) {
                this.productRepository = productRepository;
                this.companyRepository = companyRepository;
                this.appUserRepository = appUserRepository;
                this.jobService = jobService;
                this.gcs = gcs;
                this.contentRepo = contentRepo;
                this.contentAssetRepository = contentAssetRepository;
                this.projectRepository = projectRepository;
                this.tx = tx;
                this.om = om;
        }

        /**
         * 대시보드 통계 조회
         */
        public Mono<ProductDashboardStats> getDashboardStats(Long companyId) {
                return Mono.zip(
                                productRepository.countByCompanyId(companyId),
                                productRepository.countByCompanyIdAndIsActiveTrue(companyId),
                                productRepository.countByCompanyIdAndIsActiveFalse(companyId))
                                .map(tuple -> ProductDashboardStats.builder()
                                                .totalCount(tuple.getT1())
                                                .activeCount(tuple.getT2())
                                                .inactiveCount(tuple.getT3())
                                                .build());
        }

        /**
         * 제품 목록 조회 (검색 및 페이징 포함)
         */
        public Mono<Page<ProductResponse>> getProductList(Long companyId, String search, Pageable pageable) {
                Mono<Long> countMono;
                Flux<Product> productFlux;

                if (StringUtils.isNotBlank(search)) {
                        countMono = productRepository
                                        .countByCompanyIdAndNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                                                        companyId, search, search);
                        productFlux = productRepository
                                        .findByCompanyIdAndNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                                                        companyId, search, search, pageable);
                } else {
                        countMono = productRepository.countByCompanyId(companyId);
                        productFlux = productRepository.findAllByCompanyIdOrderByCreatedAtDesc(companyId, pageable);
                }

                return countMono.flatMap(total -> {
                        if (total == 0) {
                                return Mono.just(new PageImpl<>(List.of(), pageable, 0));
                        }
                        return productFlux.flatMap(product -> {
                                return Mono.zip(
                                                companyRepository.findById(product.getCompanyId())
                                                                .map(Company::getName)
                                                                .defaultIfEmpty("Unknown"),
                                                appUserRepository.findById(product.getCreatedBy())
                                                                .map(AppUser::getName)
                                                                .defaultIfEmpty("Unknown"))
                                                .map(namesTuple -> {
                                                        ProductResponse res = ProductResponse.from(product);
                                                        res.setCompanyName(namesTuple.getT1());
                                                        res.setCreatedByName(namesTuple.getT2());
                                                        return res;
                                                });
                        }).collectList().map(list -> new PageImpl<>(list, pageable, total));
                });
        }

        /**
         * 제품 상세 조회
         */
        public Mono<ProductResponse> getProductDetail(Long id) {
                return productRepository.findById(id)
                                .switchIfEmpty(Mono.error(
                                                new IllegalArgumentException("Product not found with id: " + id)))
                                .flatMap(product -> {
                                        return Mono.zip(
                                                        companyRepository.findById(product.getCompanyId())
                                                                        .map(Company::getName)
                                                                        .defaultIfEmpty("Unknown"),
                                                        appUserRepository.findById(product.getCreatedBy())
                                                                        .map(AppUser::getName)
                                                                        .defaultIfEmpty("Unknown"))
                                                        .map(namesTuple -> {
                                                                ProductResponse res = ProductResponse.from(product);
                                                                res.setCompanyName(namesTuple.getT1());
                                                                res.setCreatedByName(namesTuple.getT2());
                                                                return res;
                                                        });
                                });
        }

        /**
         * 제품 생성
         */
        @Transactional
        public Mono<ProductResponse> createProduct(ProductCreateRequest request, Long companyId, Long userId) {
                Product newProduct = Product.builder()
                                .companyId(companyId)
                                .name(request.getName())
                                .category(request.getCategory())
                                .description(request.getDescription())
                                .reviewUrl(request.getReviewUrl())
                                // .isActive(true) // Entity default
                                .createdBy(userId)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();

                return productRepository.save(newProduct)
                                .flatMap(savedProduct -> {
                                        return Mono.zip(
                                                        companyRepository.findById(savedProduct.getCompanyId())
                                                                        .map(Company::getName)
                                                                        .defaultIfEmpty("Unknown"),
                                                        appUserRepository.findById(savedProduct.getCreatedBy())
                                                                        .map(AppUser::getName)
                                                                        .defaultIfEmpty("Unknown"))
                                                        .map(namesTuple -> {
                                                                ProductResponse res = ProductResponse
                                                                                .from(savedProduct);
                                                                res.setCompanyName(namesTuple.getT1());
                                                                res.setCreatedByName(namesTuple.getT2());
                                                                return res;
                                                        });
                                })
                                .doOnSuccess(product -> log.info("Product created: {}", product.getId()));
        }

        /**
         * 제품 수정
         */
        @Transactional
        public Mono<ProductResponse> updateProduct(Long id, ProductUpdateRequest request) {
                return productRepository.findById(id)
                                .switchIfEmpty(Mono.error(
                                                new IllegalArgumentException("Product not found with id: " + id)))
                                .map(product -> product.update(request))
                                .flatMap(productRepository::save)
                                .flatMap(savedProduct -> {
                                        return Mono.zip(
                                                        companyRepository.findById(savedProduct.getCompanyId())
                                                                        .map(Company::getName)
                                                                        .defaultIfEmpty("Unknown"),
                                                        appUserRepository.findById(savedProduct.getCreatedBy())
                                                                        .map(AppUser::getName)
                                                                        .defaultIfEmpty("Unknown"))
                                                        .map(namesTuple -> {
                                                                ProductResponse res = ProductResponse
                                                                                .from(savedProduct);
                                                                res.setCompanyName(namesTuple.getT1());
                                                                res.setCreatedByName(namesTuple.getT2());
                                                                return res;
                                                        });
                                })
                                .doOnSuccess(product -> log.info("Product updated: {}", product.getId()));
        }

        /**
         * 제품 삭제
         * DELETE /api/products/{id}
         */
        @Transactional
        public Mono<Void> deleteProduct(Long id) {
                return productRepository.findById(id)
                                .switchIfEmpty(Mono.error(
                                                new IllegalArgumentException("Product not found with id: " + id)))
                                .flatMap(product -> productRepository.deleteById(id))
                                .doOnSuccess(v -> log.info("Product deleted: {}", id));
        }

        /**
         * 도면 업로드 및 목업 생성 작업 요청
         * 트랜잭션과 보상(Compensation)을 명시적으로 관리
         */
        @Transactional // [Fix] 클래스 레벨 readOnly=true 오버라이드 (WRITE 허용)
        public Mono<com.example.chillgram.domain.product.dto.PackageMockupResponse> addPackageMockup(long productId,
                        long projectId, FilePart file, AuthPrincipal principal) {
                return productRepository.findById(productId)
                                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.NOT_FOUND, "product not found")))
                                .flatMap(product -> {
                                        // 0. 권한 검증: 제품 소유권 확인
                                        if (!product.getCompanyId().equals(principal.companyId())) {
                                                return Mono.error(ApiException.of(ErrorCode.FORBIDDEN, "not allowed"));
                                        }

                                        // 1. 프로젝트 조회 & 검증
                                        return projectRepository.findById(projectId)
                                                        .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.NOT_FOUND,
                                                                        "Project not found")))
                                                        .flatMap(project -> {
                                                                // [New] Product-Project 소속 검증
                                                                if (!project.getProductId().equals(productId)) {
                                                                        return Mono.error(ApiException.of(
                                                                                        ErrorCode.FORBIDDEN,
                                                                                        "Project does not belong to this product"));
                                                                }

                                                                // [New] 베이스 이미지 검증
                                                                if (project.getUserImgGcsUrl() == null
                                                                                || project.getUserImgGcsUrl()
                                                                                                .isBlank()) {
                                                                        return Mono.error(ApiException.of(
                                                                                        ErrorCode.VALIDATION_FAILED,
                                                                                        "Project base image (userImgGcsUrl) is missing"));
                                                                }

                                                                String objectBase = "mockuptmp/" + UUID.randomUUID();

                                                                // 2. GCS 업로드 (트랜잭션 외부)
                                                                return gcs.storeFixed(file, objectBase)
                                                                                .flatMap(stored -> {
                                                                                        // Content 엔티티 생성
                                                                                        Content content = Content
                                                                                                        .builder()
                                                                                                        .companyId(principal
                                                                                                                        .companyId())
                                                                                                        .productId(productId)
                                                                                                        .projectId(projectId)
                                                                                                        // [Fix] 사용자 요청:
                                                                                                        // 원본 도면 ->
                                                                                                        // mockup_img_url,
                                                                                                        // 생성된 목업 ->
                                                                                                        // gcs_img_url
                                                                                                        .mockupImgUrl(stored
                                                                                                                        .gsUri()) // 원본
                                                                                                                                  // 도면
                                                                                                        .gcsImgUrl(null) // 생성된
                                                                                                                         // 목업은
                                                                                                                         // 아직
                                                                                                                         // 없음
                                                                                                        .status("PENDING")
                                                                                                        .contentType("IMAGE")
                                                                                                        .createdBy(principal
                                                                                                                        .userId())
                                                                                                        .createdAt(LocalDateTime
                                                                                                                        .now())
                                                                                                        .updatedAt(LocalDateTime
                                                                                                                        .now())
                                                                                                        .build();

                                                                                        ObjectNode payload = om
                                                                                                        .createObjectNode();
                                                                                        payload.put("inputUri",
                                                                                                        stored.gsUri());
                                                                                        // [New] 베이스 이미지 추가
                                                                                        payload.put("baseImageUri",
                                                                                                        project.getUserImgGcsUrl());

                                                                                        CreateJobRequest jobReq = new CreateJobRequest(
                                                                                                        JobType.DIELINE,
                                                                                                        payload);

                                                                                        // 3. DB 트랜잭션 (BASIC과 동일 방식:
                                                                                        // requestJob → Outbox →
                                                                                        // Debezium)
                                                                                        return tx.transactional(
                                                                                                        contentRepo.save(
                                                                                                                        content)
                                                                                                                        .flatMap(savedContent -> {
                                                                                                                                payload.put("contentId",
                                                                                                                                                savedContent.getId());
                                                                                                                                var asset = com.example.chillgram.domain.content.entity.ContentAsset
                                                                                                                                                .builder()
                                                                                                                                                .contentId(savedContent
                                                                                                                                                                .getId())
                                                                                                                                                .assetType("MOCKUP")
                                                                                                                                                .fileUrl(stored.fileUrl())
                                                                                                                                                .mimeType(stored.mimeType())
                                                                                                                                                .fileSize(stored.fileSize())
                                                                                                                                                .createdAt(LocalDateTime
                                                                                                                                                                .now())
                                                                                                                                                .build();

                                                                                                                                return contentAssetRepository
                                                                                                                                                .save(asset)
                                                                                                                                                .thenReturn(savedContent);
                                                                                                                        })
                                                                                                                        .flatMap(savedContent ->
                                                                                        // [Fix] Direct Publish 제거 ->
                                                                                        // Standard Outbox Pattern 복귀
                                                                                        // (Debezium 의존)
                                                                                        jobService.requestJob(
                                                                                                        projectId,
                                                                                                        jobReq,
                                                                                                        null)
                                                                                                        .map(jobId -> new com.example.chillgram.domain.product.dto.PackageMockupResponse(
                                                                                                                        jobId,
                                                                                                                        savedContent.getId(),
                                                                                                                        stored.fileUrl()))))
                                                                                                        // 4.
                                                                                                        // 보상(Compensation):
                                                                                                        // DB 트랜잭션 실패 시
                                                                                                        // GCS 업로드 취소
                                                                                                        .onErrorResume(err -> {
                                                                                                                log.error("Failed to request package mockup job. Compensating by deleting GCS object: {}",
                                                                                                                                stored.gsUri(),
                                                                                                                                err);
                                                                                                                return gcs
                                                                                                                                .delete(stored.gsUri())
                                                                                                                                .onErrorResume(delErr -> {
                                                                                                                                        log.warn("Failed to clean up GCS object after transaction failure: {}",
                                                                                                                                                        stored.gsUri(),
                                                                                                                                                        delErr);
                                                                                                                                        return Mono.empty(); // 삭제
                                                                                                                                                             // 실패는
                                                                                                                                                             // 로그만
                                                                                                                                                             // 남기고
                                                                                                                                                             // 원래
                                                                                                                                                             // 에러를
                                                                                                                                                             // 던짐
                                                                                                                                })
                                                                                                                                .then(Mono.error(
                                                                                                                                                err));
                                                                                                        });
                                                                                });
                                                        });
                                });
        }
}

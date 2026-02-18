package com.example.chillgram.domain.product.controller;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.common.security.AuthPrincipal;
import com.example.chillgram.domain.product.dto.ProductCreateRequest;
import com.example.chillgram.domain.product.dto.ProductDashboardStats;
import com.example.chillgram.domain.product.dto.ProductResponse;
import com.example.chillgram.domain.product.dto.ProductUpdateRequest;
import com.example.chillgram.domain.product.service.ProductService;
import com.example.chillgram.domain.project.dto.ProjectResponse;
import com.example.chillgram.domain.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Product", description = "제품 관리 API")
public class ProductController {

    private final ProductService productService;
    private final ProjectService projectService;

    @GetMapping("/stats")
    @Operation(summary = "대시보드 통계 조회")
    public Mono<ProductDashboardStats> getDashboardStats(
            @AuthenticationPrincipal AuthPrincipal principal) {
        if (principal == null) {
            throw ApiException.of(ErrorCode.UNAUTHORIZED, "인증 정보가 없습니다.");
        }
        return productService.getDashboardStats(principal.companyId());
    }

    @GetMapping
    @Operation(summary = "제품 목록 조회", description = "제품 목록을 검색하고 페이징하여 조회합니다.")
    public Mono<Page<ProductResponse>> getProductList(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "페이지 번호 (0..N)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "검색어") @RequestParam(required = false) String search) {

        if (principal.userId() == 0) {
            throw ApiException.of(ErrorCode.UNAUTHORIZED, "인증 정보가 없습니다.");
        }
        return productService.getProductList(principal.companyId(), search, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "제품 상세 조회")
    public Mono<ProductResponse> getProductDetail(
            @Parameter(description = "제품 ID", required = true) @PathVariable Long id) {
        return productService.getProductDetail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "제품 등록")
    public Mono<ProductResponse> createProduct(
            @Valid @RequestBody ProductCreateRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {

        if (principal == null) {
            throw ApiException.of(ErrorCode.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        return productService.createProduct(request, principal.companyId(), principal.userId());
    }

    @PutMapping("/{id}")
    @Operation(summary = "제품 수정")
    public Mono<ProductResponse> updateProduct(
            @Parameter(description = "제품 ID", required = true) @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        return productService.updateProduct(id, request);
    }

    @GetMapping("/{id}/projects")
    @Operation(summary = "제품별 프로젝트 목록 조회")
    public Mono<List<ProjectResponse>> getProjectsByProduct(
            @Parameter(description = "제품 ID", required = true) @PathVariable Long id) {
        return projectService.getProjectsByProduct(id);
    }

    @PostMapping("/{id}/add_package")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "패키지 목업 생성 (도면 업로드)", description = "도면 이미지를 업로드하고 프로젝트에 연결된 패키지 목업 생성 작업을 요청합니다.")
    public Mono<com.example.chillgram.domain.product.dto.PackageMockupResponse> addPackage(
            @Parameter(description = "제품 ID", required = true) @PathVariable Long id,
            @Parameter(description = "프로젝트 ID", required = true) @RequestParam Long projectId,
            @Parameter(description = "베이스 이미지 URL (Basic에서 선택)", required = true) @RequestParam String baseImageUrl,
            @RequestPart("file") FilePart file,
            @AuthenticationPrincipal AuthPrincipal principal) {

        if (principal == null) {
            throw ApiException.of(ErrorCode.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        return productService.addPackageMockup(id, projectId, baseImageUrl, file, principal);
    }

}

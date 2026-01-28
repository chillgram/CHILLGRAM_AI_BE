package com.example.chillgram.domain.company.api;

import com.example.chillgram.domain.company.api.dto.CompanyResponse;
import com.example.chillgram.domain.company.service.CompanyQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 회사(Company) 조회용 공개 API를 제공하는 컨트롤러.
 * - 회원가입 화면에서 회사 선택 목록을 제공하기 위한 조회 전용 엔드포인트
 */
@Tag(name = "Company", description = "회사 정보 API")
@RestController
public class CompanyController {

    private final CompanyQueryService companyService;

    public CompanyController(CompanyQueryService companyService) {
        this.companyService = companyService;
    }

    /**
     * 회사 목록 조회 API
     * @return 회사 목록 스트림
     */
    @SecurityRequirements
    @Operation(summary = "회사 목록 조회", description = "회원가입 화면에서 회사 선택 드롭다운/리스트를 구성하기 위한 조회 API")
    @GetMapping("/api/companies")
    public Flux<CompanyResponse> listCompanies() {
        return companyService.listCompanies();
    }
}
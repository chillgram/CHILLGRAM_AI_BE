package com.example.chillgram.domain.company.api;

import com.example.chillgram.domain.company.api.dto.CompanyResponse;
import com.example.chillgram.domain.company.service.CompanyQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 회사(Company) 조회용 공개 API를 제공하는 컨트롤러.
 * - 회원가입 화면에서 회사 선택 목록을 제공하기 위한 조회 전용 엔드포인트
 */
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
    @GetMapping("/api/companies")
    public Flux<CompanyResponse> listCompanies() {
        return companyService.listCompanies();
    }
}
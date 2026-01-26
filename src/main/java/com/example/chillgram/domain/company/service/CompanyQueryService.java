package com.example.chillgram.domain.company.service;

import com.example.chillgram.domain.company.api.dto.CompanyResponse;
import com.example.chillgram.domain.company.repository.CompanyRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 회사(Company) 조회 전용 서비스.
 */
@Service
public class CompanyQueryService {

    private final com.example.chillgram.domain.company.repository.CompanyRepository companyRepository;

    public CompanyQueryService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    /**
     * 회사 목록 조회.
     * @return 회사 목록 스트림(Flux)
     */
    public Flux<CompanyResponse> listCompanies() {
        return companyRepository.findAllByOrderByNameAsc()
                .map(c -> new CompanyResponse(c.getCompanyId(), c.getName()));
    }
}
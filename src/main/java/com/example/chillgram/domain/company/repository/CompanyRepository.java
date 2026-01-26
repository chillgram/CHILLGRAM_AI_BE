package com.example.chillgram.domain.company.repository;

import com.example.chillgram.domain.company.domain.Company;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface CompanyRepository extends ReactiveCrudRepository<Company, Long> {

    @Query("SELECT cm.company_id, cm.name FROM company cm ORDER BY name ASC")
    Flux<Company> findAllByOrderByNameAsc();
}
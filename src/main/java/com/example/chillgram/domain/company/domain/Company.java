package com.example.chillgram.domain.company.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("company")
public class Company {

    @Id
    private Long companyId;

    private String name;

    private OffsetDateTime createdAt;

    public Company() {}

    public Company(Long companyId, String name, OffsetDateTime createdAt) {
        this.companyId = companyId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public Long getCompanyId() { return companyId; }
    public String getName() { return name; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public void setName(String name) { this.name = name; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
package com.example.chillgram.domain.qa.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("qa_category")
public class QaCategory {

    @Id
    @Column("category_id")
    private Long categoryId;

    private String name;

    private String description;
}

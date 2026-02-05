package com.example.chillgram.domain.content.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("content")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Content {

    @Id
    @Column("content_id")
    private Long id;

    @Column("project_id")
    private Long projectId;

    // 통계용으로 필드 최소화 (필요시 추가)
    @Column("created_at")
    private LocalDateTime createdAt;
}

package com.example.chillgram.domain.project.repository;

import com.example.chillgram.domain.project.entity.Project;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

public interface ProjectRepository extends R2dbcRepository<Project, Long> {
    Flux<Project> findAllByProductId(Long productId);

    @Query("""
            SELECT p.project_id AS projectId,
                   p.title AS title,
                   p.project_type AS projectType,
                   p.status AS status,
                   p.ad_message_focus AS adMessageFocus,
                   p.ad_message_target AS adMessageTarget,
                   p.created_at AS createdAt,
                   p.userimg_gcs_url AS userImgGcsUrl,
                   p.dieline_gcs_url AS dielineGcsUrl,
                   p.mockup_result_url AS mockupResultUrl,
                   COALESCE(cnt.content_count, 0) AS contentCount
              FROM project p
              LEFT JOIN (SELECT project_id, count(*) AS content_count
                           FROM content
                          GROUP BY project_id) cnt
                ON cnt.project_id = p.project_id
             WHERE p.product_id = :productId
             ORDER BY p.created_at DESC
            """)
    Flux<ProjectWithCount> findAllByProductIdWithCount(Long productId);

    record ProjectWithCount(
            Long projectId,
            String title,
            Project.ProjectType projectType,
            String status,
            Integer adMessageFocus,
            Integer adMessageTarget,
            LocalDateTime createdAt,
            String userImgGcsUrl,
            String dielineGcsUrl,
            String mockupResultUrl,
            Long contentCount) {
    }
}

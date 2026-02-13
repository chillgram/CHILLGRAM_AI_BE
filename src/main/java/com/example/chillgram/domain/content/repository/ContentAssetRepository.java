package com.example.chillgram.domain.content.repository;

import com.example.chillgram.domain.content.entity.ContentAsset;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface ContentAssetRepository extends R2dbcRepository<ContentAsset, Long> {

    Flux<ContentAsset> findByContentIdOrderBySortOrderAsc(Long contentId);
}

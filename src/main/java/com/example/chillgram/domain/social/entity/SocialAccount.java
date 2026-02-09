package com.example.chillgram.domain.social.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Getter
@Table("social_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SocialAccount {

    @Id
    @Column("social_account_id")
    private Long socialAccountId;

    @Column("company_id")
    private Long companyId;

    @Column("platform")
    private String platform;

    @Column("account_label")
    private String accountLabel;

    @Column("token_ref")
    private String tokenRef;

    @Column("is_active")
    private Boolean isActive;

    @Column("connected_at")
    private OffsetDateTime connectedAt;
}
package com.example.chillgram.domain.social.service.core;

import com.example.chillgram.domain.social.config.SocialProperties;
import com.example.chillgram.domain.social.dto.OAuthTokenPayload;
import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.cloud.secretmanager.v1.Replication;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretName;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;

/**
 * 역할: YouTube OAuth 토큰을 GCP Secret Manager에 저장/조회하는 전용 컴포넌트
 * - save(): 토큰(access/refresh/만료시각)을 Secret에 새 버전으로 저장하고 secret 경로(tokenRef)를 반환
 * - readLatest(): tokenRef의 latest 버전을 읽어서 OAuthTokenPayload로 복원
 * - Secret Manager 호출은 블로킹이므로 boundedElastic에서 실행
 */
@Component
@RequiredArgsConstructor
public class YoutubeTokenStore {

    // GCP 프로젝트 ID 등 설정값
    private final SocialProperties props;
    private final SecretManagerServiceClient client;

    // 토큰을 Secret Manager에 저장하고 secret 경로(projects/.../secrets/...)를 반환
    public Mono<String> save(long companyId, OAuthTokenPayload payload) {
        return Mono.fromCallable(() -> saveBlocking(companyId, payload))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // tokenRef의 latest 버전을 읽어 토큰 payload를 반환
    public Mono<Optional<OAuthTokenPayload>> readLatest(String tokenRef) {
        return Mono.fromCallable(() -> readLatestBlocking(tokenRef))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // (blocking) Secret Manager에 토큰을 새 버전으로 저장
    private String saveBlocking(long companyId, OAuthTokenPayload payload) {
        String projectId = props.gcp().projectId();
        if (projectId == null || projectId.isBlank()) throw new IllegalStateException("social.gcp.project-id 비어있음");

        String secretId = "yt-company-" + companyId;
        ProjectName parent = ProjectName.of(projectId);
        SecretName secretName = SecretName.of(projectId, secretId);

        try {
            // 1. Secret 생성 (이미 있으면 pass)
            try {
                client.createSecret(parent, secretId, Secret.newBuilder()
                        .setReplication(Replication.newBuilder()
                                .setAutomatic(Replication.Automatic.newBuilder().build())
                                .build())
                        .build());
            } catch (AlreadyExistsException ignored) {}

            // 2. 새 버전 추가 (기존 연결을 재사용하므로 매우 빠름)
            client.addSecretVersion(secretName, SecretPayload.newBuilder()
                    .setData(ByteString.copyFrom(payload.toJsonBytes()))
                    .build());

            return secretName.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Secret Manager 저장 실패", e);
        }
    }

    // (blocking) tokenRef의 latest 버전을 읽어 payload로 복원
    private Optional<OAuthTokenPayload> readLatestBlocking(String tokenRef) {
        String versionPath = tokenRef.endsWith("/versions/latest") ? tokenRef : tokenRef + "/versions/latest";

        try {
            // 기존 연결을 재사용하여 최신 버전 조회
            AccessSecretVersionResponse res = client.accessSecretVersion(SecretVersionName.parse(versionPath));
            return Optional.of(OAuthTokenPayload.fromJsonBytes(res.getPayload().getData().toByteArray()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}

package com.example.chillgram.domain.social.service.core;

import com.example.chillgram.domain.social.config.SocialProperties;
import com.example.chillgram.domain.social.dto.OAuthTokenPayload;
import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.cloud.secretmanager.v1.*;
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

        // 회사별로 secret 하나를 두고, 토큰 갱신 시마다 version을 추가하는 방식
        String secretId = "yt-company-" + companyId;

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            ProjectName parent = ProjectName.of(projectId);
            SecretName secretName = SecretName.of(projectId, secretId);

            // secret이 없으면 생성
            try {
                client.createSecret(parent, secretId, Secret.newBuilder()
                        .setReplication(Replication.newBuilder()
                                .setAutomatic(Replication.Automatic.newBuilder().build())
                                .build())
                        .build());
            } catch (AlreadyExistsException ignored) {}

            // 새 버전으로 토큰 데이터 저장(JSON bytes)
            client.addSecretVersion(secretName, SecretPayload.newBuilder()
                    .setData(ByteString.copyFrom(payload.toJsonBytes()))
                    .build());

            // DB에는 secretName(tokenRef)만 저장해두고, 읽을 때 /versions/latest로 접근
            return secretName.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Secret Manager 저장 실패", e);
        }
    }

    // (blocking) tokenRef의 latest 버전을 읽어 payload로 복원
    private Optional<OAuthTokenPayload> readLatestBlocking(String tokenRef) {
        String versionPath = tokenRef.endsWith("/versions/latest") ? tokenRef : tokenRef + "/versions/latest";

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            AccessSecretVersionResponse res = client.accessSecretVersion(SecretVersionName.parse(versionPath));
            return Optional.of(OAuthTokenPayload.fromJsonBytes(res.getPayload().getData().toByteArray()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}

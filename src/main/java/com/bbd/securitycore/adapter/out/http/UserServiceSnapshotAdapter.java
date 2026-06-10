package com.bbd.securitycore.adapter.out.http;

import com.bbd.securitycore.application.port.out.LoadUserSnapshotPort;
import com.bbd.securitycore.domain.UserSnapshot;
import org.springframework.web.client.RestClientException;

/*
 User Service에서 UserSnapshot을 조회하는 HTTP adapter.

 Redis 캐시에 UserSnapshot이 없을 때,
 HTTP Service Interface를 통해 User Service에서 최신 사용자 스냅샷을 가져온다.

 이 adapter는 application 계층의 LoadUserSnapshotPort를 구현한다.
 */
public class UserServiceSnapshotAdapter implements LoadUserSnapshotPort {

    private final UserSnapshotHttpClient userSnapshotHttpClient;

    public UserServiceSnapshotAdapter(UserSnapshotHttpClient userSnapshotHttpClient) {
        this.userSnapshotHttpClient = userSnapshotHttpClient;
    }

    /*
     Keycloak sub 기준으로 User Service에서 UserSnapshot을 조회한다.

     User Service에서 사용자를 찾지 못하거나,
     호출 중 오류가 발생하면 null을 반환한다.

     null 처리는 GetCurrentUserSnapshotService에서
     USER_SNAPSHOT_NOT_FOUND 예외로 변환한다.
     */
    @Override
    public UserSnapshot loadByKeycloakSub(String keycloakSub) {
        if (keycloakSub == null || keycloakSub.isBlank()) {
            return null;
        }

        try {
            return userSnapshotHttpClient.getUserSnapshot(keycloakSub);
        } catch (RestClientException exception) {
            return null;
        }
    }
}
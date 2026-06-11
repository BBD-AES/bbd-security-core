package com.bbd.securitycore.adapter.out.http;

import com.bbd.securitycore.application.port.out.LoadUserSnapshotPort;
import com.bbd.securitycore.domain.UserSnapshot;
import org.springframework.web.client.HttpClientErrorException;

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

     keycloakSub가 비어 있으면 조회할 수 없으므로 null을 반환한다.

     User Service가 404 Not Found를 반환하면
     해당 keycloakSub에 매핑되는 사용자가 없는 것으로 보고 null을 반환한다.

     null 처리는 GetCurrentUserSnapshotService에서
     USER_SNAPSHOT_NOT_FOUND 예외로 변환한다.

     그 외의 HTTP 오류, 네트워크 오류, 역직렬화 오류는
     사용자 없음이 아니라 시스템 호출 실패이므로 그대로 전파한다.
     */
    @Override
    public UserSnapshot loadByKeycloakSub(String keycloakSub) {
        if (keycloakSub == null || keycloakSub.isBlank()) {
            return null;
        }

        try {
            return userSnapshotHttpClient.getUserSnapshot(keycloakSub);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }
}
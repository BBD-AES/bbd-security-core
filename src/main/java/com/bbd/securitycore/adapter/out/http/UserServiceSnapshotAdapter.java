package com.bbd.securitycore.adapter.out.http;

import com.bbd.securitycore.application.port.out.LoadUserSnapshotPort;
import com.bbd.securitycore.domain.UserSnapshot;
import com.bbd.securitycore.global.error.BbdSecurityException;
import com.bbd.securitycore.global.error.dto.ErrorCode;
import com.bbd.securitycore.global.logging.SecurityLogUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/*
 User Service에서 UserSnapshot을 조회하는 HTTP adapter.

 Redis 캐시에 UserSnapshot이 없을 때,
 HTTP Service Interface를 통해 User Service에서 최신 사용자 스냅샷을 가져온다.

 이 adapter는 application 계층의 LoadUserSnapshotPort를 구현한다.
 */
@Slf4j
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

     User Service 5xx, 연결 실패, timeout, 응답 파싱 실패는
     사용자 없음이 아니라 인가 의존성 일시 불가이므로 503으로 변환한다.
     */
    @Override
    public UserSnapshot loadByKeycloakSub(String keycloakSub) {
        if (keycloakSub == null || keycloakSub.isBlank()) {
            log.debug("User Service UserSnapshot 조회를 건너뜁니다. keycloakSub가 비어 있습니다.");
            return null;
        }

        try {
            log.debug(
                    "User Service UserSnapshot 조회를 시작합니다. keycloakSubHash={}",
                    keycloakSubHash(keycloakSub)
            );

            UserSnapshot snapshot = userSnapshotHttpClient.getUserSnapshot(keycloakSub);

            if (snapshot == null) {
                log.warn(
                        "User Service UserSnapshot 조회 결과가 null입니다. keycloakSubHash={}",
                        keycloakSubHash(keycloakSub)
                );
            } else {
                log.debug(
                        "User Service UserSnapshot 조회 성공. keycloakSubHash={}, userId={}, status={}, role={}",
                        keycloakSubHash(keycloakSub),
                        snapshot.userId(),
                        snapshot.status(),
                        snapshot.role()
                );
            }

            return snapshot;
        } catch (HttpClientErrorException.NotFound e) {
            log.info(
                    "User Service에서 UserSnapshot을 찾지 못했습니다. status={}, keycloakSubHash={}",
                    e.getStatusCode(),
                    keycloakSubHash(keycloakSub)
            );
            return null;
        } catch (HttpClientErrorException e) {
            log.warn(
                    "User Service UserSnapshot 조회가 4xx 응답으로 실패했습니다. status={}, keycloakSubHash={}",
                    e.getStatusCode(),
                    keycloakSubHash(keycloakSub)
            );
            throw e;
        } catch (RestClientException e) {
            log.warn(
                    "User Service UserSnapshot 조회 중 통신/응답 처리 오류가 발생했습니다. keycloakSubHash={}",
                    keycloakSubHash(keycloakSub),
                    e
            );
            throw new BbdSecurityException(ErrorCode.USER_SERVICE_UNAVAILABLE, e);
        }
    }

    private String keycloakSubHash(String keycloakSub) {
        return SecurityLogUtils.fingerprint(keycloakSub);
    }
}

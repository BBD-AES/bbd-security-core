package com.bbd.securitycore.application.port.out;

import com.bbd.securitycore.domain.UserSnapshot;

/*
 User Service에서 UserSnapshot을 조회하기 위한 outbound port.

 Redis 캐시에 사용자 스냅샷이 없을 때,
 application service는 이 포트를 통해 User Service의 내부 API를 호출한다.
 */
public interface LoadUserSnapshotPort {

    /*
     keycloakSub를 기준으로 User Service에서 인가용 UserSnapshot을 조회한다.

     예: ET /internal/users/snapshots/{keycloakSub}

     사용자를 찾을 수 없는 경우 null이면 USER_SNAPSHOT_NOT_FOUND 예외로 처리한다.
     */
    UserSnapshot loadByKeycloakSub(String keycloakSub);
}
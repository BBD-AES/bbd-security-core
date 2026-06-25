package com.bbd.securitycore.application.port.out;

import com.bbd.securitycore.domain.UserSnapshot;

import java.util.Optional;

/*
 Redis 같은 캐시 저장소에서 UserSnapshot을 조회하기 위한 outbound port.

 구현체는 adapter.out.redis 계층에 두고,
 keycloakSub를 기준으로 캐시된 사용자 스냅샷을 조회한다.
 */
public interface LoadUserSnapshotCachePort {

    /*
     keycloakSub를 기준으로 캐시에 저장된 UserSnapshot을 조회한다.

     캐시에 값이 있으면 Optional에 담아 반환하고,
     캐시에 없으면 Optional.empty()를 반환한다.

     Redis miss가 발생한 경우 application service는 User Service 조회 포트를 통해
     원본 사용자 스냅샷을 다시 가져올 수 있다.
     */
    Optional<UserSnapshot> findByKeycloakSub(String keycloakSub);

    /*
     keycloakSub에 해당하는 사용자가 없다는 NOT_FOUND 결과가 캐시에 있는지 조회한다.

     기본 구현은 false를 반환해 기존 커스텀 구현체와의 호환성을 유지한다.
     */
    default boolean isNotFoundCached(String keycloakSub) {
        return false;
    }
}

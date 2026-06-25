package com.bbd.securitycore.application.port.out;

import com.bbd.securitycore.domain.UserSnapshot;

/*
 UserSnapshot을 Redis 같은 캐시 저장소에 저장하기 위한 outbound port.

 User Service에서 조회한 사용자 스냅샷을 캐시에 저장해두면,
 이후 같은 사용자의 요청에서는 User Service를 매번 호출하지 않고
 Redis 캐시에서 빠르게 인가 정보를 가져올 수 있다.
 */
public interface SaveUserSnapshotCachePort {

    /*
     UserSnapshot을 캐시에 저장한다.

     구현체에서는 보통 다음 정보를 기준으로 저장한다.

     - key: user:snapshot:{keycloakSub}
     - value: UserSnapshot
     - TTL: 서비스 정책에 따른 만료 시간
     */
    void save(UserSnapshot snapshot);

    /*
     keycloakSub에 해당하는 사용자가 없다는 NOT_FOUND 결과를 짧게 캐싱한다.

     기본 구현은 아무 것도 하지 않아 기존 커스텀 구현체와의 호환성을 유지한다.
     */
    default void saveNotFound(String keycloakSub) {
    }
}

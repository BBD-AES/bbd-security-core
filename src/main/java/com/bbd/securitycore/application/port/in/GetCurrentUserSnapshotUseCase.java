package com.bbd.securitycore.application.port.in;

import com.bbd.securitycore.application.model.CurrentUserSnapshotResult;

/*
 현재 요청 사용자에 대한 UserSnapshot을 조회하기 위한 inbound port.

 각 MSA는 이 유스케이스를 통해
 JWT에서 추출한 사용자 식별자(keycloakSub)를 기준으로
 Redis 캐시 또는 User Service에서 인가용 사용자 스냅샷을 가져온다.
 */
public interface GetCurrentUserSnapshotUseCase {

    /*
     현재 인증된 사용자의 인가용 스냅샷을 조회한다.
     내부 구현에서는 보통 다음 순서로 동작한다.

     1. 현재 요청의 JWT에서 keycloakSub 추출
     2. Redis에서 UserSnapshot 조회
     3. Redis miss 시 User Service에서 조회
     4. 조회한 UserSnapshot을 Redis에 저장
     5. application 결과 모델로 반환
     */
    CurrentUserSnapshotResult getCurrentUserSnapshot();
}
package com.bbd.securitycore.adapter.out.http;

import com.bbd.securitycore.domain.UserSnapshot;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/*
 User Service의 UserSnapshot 조회 API를 호출하는 HTTP Service Interface.

 @ImportHttpServices가 이 인터페이스를 기반으로
 HTTP client proxy Bean을 생성한다.

 실제 HTTP 요청은 Spring HTTP Service infrastructure가 처리한다.
 */
@HttpExchange
public interface UserSnapshotHttpClient {

    /*
     Keycloak sub 기준으로 UserSnapshot을 조회한다.

     요청 예:
     GET /internal/users/snapshot/{keycloakSub}
     */
    @GetExchange("/api/v1/users/internal/snapshot")
    UserSnapshot getUserSnapshot(@RequestParam("keycloakSub") String keycloakSub);
}
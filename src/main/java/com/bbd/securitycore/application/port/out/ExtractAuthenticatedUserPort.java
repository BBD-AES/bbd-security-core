package com.bbd.securitycore.application.port.out;

/*
 현재 요청에서 인증된 사용자 식별자를 추출하기 위한 outbound port.

 application 계층은 Spring Security의 Authentication, Jwt, SecurityContext 같은
 프레임워크 타입에 직접 의존하지 않는다.

 대신 adapter.in.security 계층에서 현재 인증 정보를 읽고,
 이 포트를 통해 keycloakSub만 application 계층에 전달한다.
 */
public interface ExtractAuthenticatedUserPort {

    /*
     현재 요청의 인증 정보에서 Keycloak 사용자 고유 식별자(sub)를 추출한다.

     예:
     - JWT의 sub claim
     - Spring Security Authentication 내부의 principal 정보

     인증되지 않은 요청이거나 sub를 추출할 수 없는 경우 null을 반환할 수 있다.
     */
    String getCurrentKeycloakSub();
}
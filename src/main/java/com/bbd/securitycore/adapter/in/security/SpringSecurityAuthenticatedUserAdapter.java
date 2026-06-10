package com.bbd.securitycore.adapter.in.security;

import com.bbd.securitycore.application.port.out.ExtractAuthenticatedUserPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/*
 Spring Security의 SecurityContext에서 현재 인증 사용자의 keycloakSub를 추출하는 adapter.

 각 MSA는 Resource Server 설정을 통해 Access Token을 먼저 검증한다.
 검증이 성공하면 Spring Security는 Jwt 객체를 Authentication의 principal로 저장한다.

 이 adapter는 그 Jwt에서 Keycloak 사용자 고유 식별자(sub)를 꺼내
 application 계층에 전달한다.
 */
public class SpringSecurityAuthenticatedUserAdapter implements ExtractAuthenticatedUserPort {

    /*
     현재 요청의 인증 정보에서 Keycloak 사용자 고유 식별자(sub)를 추출한다.
     인증 정보가 없거나, 인증되지 않았거나, principal이 Jwt가 아닌 경우 null을 반환한다.
     이후 application service에서 null이면 AUTH_UNAUTHENTICATED 예외로 처리한다.
     */
    @Override
    public String getCurrentKeycloakSub() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Jwt jwt) {
            return jwt.getSubject();
        }

        return null;
    }
}
package com.bbd.securitycore.adapter.out.http;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.io.IOException;

/*
 현재 요청의 SecurityContext에 있는 Jwt access token을
 내부 HTTP 요청의 Authorization 헤더로 전달하는 interceptor.

 예:
 Authorization: Bearer <access-token>

 Gateway를 거치지 않는 MSA 내부 HTTP 호출에서도
 호출받는 MSA가 동일한 access token을 검증할 수 있게 한다.

 적용 대상:
 - User Service 호출
 - Item Service 호출
 - Sales Service 호출
 - Inventory Service 호출
 - Procurement Service 호출
 - 기타 HTTP Service Interface 기반 내부 호출
 */
public class SecurityContextAccessTokenRelayInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String tokenValue = jwt.getTokenValue();

            if (tokenValue != null && !tokenValue.isBlank()) {
                request.getHeaders().setBearerAuth(tokenValue);
            }
        }

        return execution.execute(request, body);
    }
}
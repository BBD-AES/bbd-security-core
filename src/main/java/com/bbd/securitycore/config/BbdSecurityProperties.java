package com.bbd.securitycore.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/*
 bbd-security-core의 공통 보안 설정 값을 받는 properties 클래스.
 각 MSA는 application.yml에서 bbd.security.* 설정을 통해
 공통 보안 설정을 조정할 수 있다.
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "bbd.security")
public class BbdSecurityProperties {

    /*
     bbd-security-core의 기본 SecurityFilterChain 자동 등록 여부.
     true이면 공통 Resource Server 보안 설정을 자동 등록한다.
     false이면 각 MSA가 직접 SecurityFilterChain을 구성해야 한다.
     */
    private boolean enabled = true;

    /*
     인증 없이 허용할 경로 목록.

     기본 정책:
     - error
     - health check
     - swagger 문서

     실제 업무 API는 기본적으로 permitAll에 넣지 않는다.
     서비스별 공개 API가 필요하면 각 MSA의 application.yml에서 별도로 추가한다.

     /item/swagger-ui/** 처럼 Gateway prefix가 MSA까지 전달되는 경우도 대비해 기본 허용한다.
     */
    private List<String> permitAllPaths = List.of(
            "/error",
            "/actuator/health",
            "/health",

            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",

            "/*/swagger-ui/**",
            "/*/swagger-ui.html",
            "/*/v3/api-docs/**"
    );
}
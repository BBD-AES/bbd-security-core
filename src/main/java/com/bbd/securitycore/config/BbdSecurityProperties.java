package com.bbd.securitycore.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/*
bbd-security-core의 공통 보안 설정 값을 받는 properties 클래스.

각 MSA는 application.yml에서 bbd.security.* 설정을 통해
공통 보안 설정을 조정할 수 있다.

User Service HTTP 호출의 base-url은
Spring HTTP Service group 설정에서 관리한다.

예:
spring:
http:
service:
groups:
bbd-user-service:
base-url: http://bbd-user:8080
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

    /*
     UserSnapshot Redis 캐시 key prefix.

     실제 Redis key 예:
     user:snapshot:{keycloakSub}
     */
    private String userSnapshotCacheKeyPrefix = "user:snapshot:";

    /*
     UserSnapshot Redis 캐시 TTL.

     기본값은 300초, 즉 5분이다.

     너무 길면 권한 변경 반영이 늦어지고,
     너무 짧으면 User Service 호출이 많아진다.
     */
    private long userSnapshotCacheTtlSeconds = 300;

    /*
     User Service Snapshot 조회 HTTP 호출 설정.

     이 호출은 @RequireRole 인가 경로의 캐시 miss 시 동기적으로 실행되므로,
     무한 대기로 요청 스레드가 고갈되지 않도록 보수적인 기본 timeout을 둔다.
     */
    private UserService userService = new UserService();

    @Setter
    @Getter
    public static class UserService {

        /*
         User Service 연결 수립 timeout.

         application.yml 예:
         bbd.security.user-service.connect-timeout: 1s
         */
        private Duration connectTimeout = Duration.ofSeconds(1);

        /*
         User Service 응답 read timeout.

         application.yml 예:
         bbd.security.user-service.read-timeout: 3s
         */
        private Duration readTimeout = Duration.ofSeconds(3);
    }

}

package com.bbd.securitycore.config;

import com.bbd.securitycore.adapter.in.security.SpringSecurityAuthenticatedUserAdapter;
import com.bbd.securitycore.application.port.in.AuthorizeUserUseCase;
import com.bbd.securitycore.application.port.in.GetCurrentUserSnapshotUseCase;
import com.bbd.securitycore.application.port.out.ExtractAuthenticatedUserPort;
import com.bbd.securitycore.application.port.out.LoadUserSnapshotCachePort;
import com.bbd.securitycore.application.port.out.LoadUserSnapshotPort;
import com.bbd.securitycore.application.port.out.SaveUserSnapshotCachePort;
import com.bbd.securitycore.application.service.AuthorizeUserService;
import com.bbd.securitycore.application.service.GetCurrentUserSnapshotService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/*
 bbd-security-core의 자동 설정 클래스.

 각 MSA가 이 라이브러리를 의존성으로 추가하면,
 Access Token 검증과 공통 인가에 필요한 기본 Bean들을 자동 등록한다.

 이 클래스가 담당하는 것:

 1. 각 MSA의 Access Token 검증용 SecurityFilterChain 등록
 2. 현재 인증 사용자 sub 추출 adapter 등록
 3. UserSnapshot 조회 유스케이스 등록
 4. 인가 검사 유스케이스 등록

 기본 정책은 다음과 같다.

 - 각 MSA는 별도 SecurityConfig를 만들지 않는다.
 - bbd-security-core가 Resource Server 기반 보안 체인을 등록한다.
 - 정말 예외적으로 MSA가 보안을 직접 구성해야 하면 bbd.security.enabled=false로 끈다.
 bbd:
  security:
    enabled: false
 - 각 MSA는 별도의 YML 파일에 아래와 같이 기입한다.
 spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI}
 */
@AutoConfiguration
@ConditionalOnClass(SecurityFilterChain.class)
@EnableConfigurationProperties(BbdSecurityProperties.class)
public class BbdSecurityAutoConfiguration {

    /*
     각 MSA에 기본 SecurityFilterChain을 등록한다.

     Gateway의 Bearer Token 체인과 같은 Resource Server 방식이다.

     Authorization: Bearer <access-token> 요청에 대해 다음을 검증한다.

     - JWT 서명
     - issuer
     - expiration
     - Spring Security Resource Server 기본 검증

     MSA는 웹 로그인/OIDC logout/CSRF 쿠키를 처리하지 않으므로
     세션은 STATELESS, CSRF는 disable로 둔다.
     */
    @Bean
    // 프레임워크 기본 보안 체인으로 등록하되, 나중에 특정 MSA가 더 높은 우선순위의 특수 체인을 만들 수 있게 여지를 남김
    @Order(100)
    // 설정값에 따라 Bean을 등록할지 말지 결정하는 어노테이션 -> MSA의 yml 파일과 관련있다.
    @ConditionalOnProperty(
            prefix = "bbd.security",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public SecurityFilterChain bbdSecurityFilterChain(
            HttpSecurity http,
            BbdSecurityProperties properties
    ) throws Exception {

        String[] permitAllPaths = properties.getPermitAllPaths().toArray(String[]::new);

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(permitAllPaths).permitAll()
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                );

        return http.build();
    }

    /*
     Spring Security의 SecurityContext에서 현재 인증 사용자의 keycloakSub를 추출하는 adapter.

     GetCurrentUserSnapshotService는 이 포트를 통해 현재 요청 사용자의 keycloakSub를 얻는다.
     */
    @Bean
    @ConditionalOnMissingBean(ExtractAuthenticatedUserPort.class)
    public ExtractAuthenticatedUserPort extractAuthenticatedUserPort() {
        return new SpringSecurityAuthenticatedUserAdapter();
    }

    /*
     현재 요청 사용자의 UserSnapshot을 조회하는 유스케이스를 등록한다.

     내부적으로 다음 순서로 동작한다.

     1. 현재 인증 사용자의 keycloakSub 추출
     2. Redis 캐시 조회
     3. Redis miss 시 User Service 호출
     4. 조회 결과 Redis 캐시 저장
     5. CurrentUserSnapshotResult 반환

     Redis adapter와 User Service HTTP adapter가 아직 없다면
     실제 MSA 실행 시 Bean 생성 실패가 날 수 있다.
     다음 단계에서 해당 adapter들을 추가한다.
     */
    @Bean
    @ConditionalOnMissingBean(GetCurrentUserSnapshotUseCase.class)
    public GetCurrentUserSnapshotUseCase getCurrentUserSnapshotUseCase(
            ExtractAuthenticatedUserPort extractAuthenticatedUserPort,
            LoadUserSnapshotCachePort loadUserSnapshotCachePort,
            LoadUserSnapshotPort loadUserSnapshotPort,
            SaveUserSnapshotCachePort saveUserSnapshotCachePort
    ) {
        return new GetCurrentUserSnapshotService(
                extractAuthenticatedUserPort,
                loadUserSnapshotCachePort,
                loadUserSnapshotPort,
                saveUserSnapshotCachePort
        );
    }

    /*
     ACTIVE, role, tenancy 같은 공통 인가 규칙을 검사하는 유스케이스를 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean(AuthorizeUserUseCase.class)
    public AuthorizeUserUseCase authorizeUserUseCase() {
        return new AuthorizeUserService();
    }
}
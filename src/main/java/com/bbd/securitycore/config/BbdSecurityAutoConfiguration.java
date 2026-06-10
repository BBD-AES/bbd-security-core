package com.bbd.securitycore.config;

import com.bbd.securitycore.adapter.in.security.SpringSecurityAuthenticatedUserAdapter;
import com.bbd.securitycore.adapter.out.http.UserServiceSnapshotAdapter;
import com.bbd.securitycore.adapter.out.redis.RedisUserSnapshotCacheAdapter;
import com.bbd.securitycore.application.port.in.AuthorizeUserUseCase;
import com.bbd.securitycore.application.port.in.GetCurrentUserSnapshotUseCase;
import com.bbd.securitycore.application.port.out.ExtractAuthenticatedUserPort;
import com.bbd.securitycore.application.port.out.LoadUserSnapshotCachePort;
import com.bbd.securitycore.application.port.out.LoadUserSnapshotPort;
import com.bbd.securitycore.application.port.out.SaveUserSnapshotCachePort;
import com.bbd.securitycore.application.service.AuthorizeUserService;
import com.bbd.securitycore.application.service.GetCurrentUserSnapshotService;
import com.bbd.securitycore.domain.UserSnapshot;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/*
 bbd-security-core의 자동 설정 클래스.

 각 MSA가 이 라이브러리를 의존성으로 추가하면,
 Access Token 검증과 공통 인가에 필요한 기본 Bean들을 자동 등록한다.

 이 클래스가 담당하는 것:

 1. 각 MSA의 Access Token 검증용 SecurityFilterChain 등록
 2. 현재 인증 사용자 sub 추출 adapter 등록
 3. UserSnapshot Redis 캐시 adapter 등록
 4. UserSnapshot 조회 유스케이스 등록
 5. 인가 검사 유스케이스 등록

 기본 정책은 다음과 같다.

 - 각 MSA는 별도 SecurityConfig를 만들지 않는다.
 - bbd-security-core가 Resource Server 기반 보안 체인을 등록한다.
 - 정말 예외적으로 MSA가 보안을 직접 구성해야 하면 bbd.security.enabled=false로 끈다.
 - 각 MSA는 별도의 yml 파일에 issuer-uri를 설정한다.

 예:

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
    @Order(100)
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
                .csrf(AbstractHttpConfigurer::disable)
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
     UserSnapshot 전용 RedisTemplate을 등록한다.

     이 RedisTemplate은 bbd-security-core가
     UserSnapshot 객체를 Redis에 저장하고 조회할 때 사용한다.

     key는 사람이 읽기 쉬운 문자열 형태로 저장하고,
     value는 UserSnapshot 객체를 JSON 형태로 직렬화해서 저장한다.

     RedisConnectionFactory가 존재할 때만 등록된다.
     즉, 각 MSA가 Redis host/port 같은 Redis 설정을 가지고 있어야
     Spring Boot가 RedisConnectionFactory를 만들고,
     그 이후에 이 Bean도 생성된다.
     */
    @Bean(name = "userSnapshotRedisTemplate")
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(name = "userSnapshotRedisTemplate")
    public RedisTemplate<String, UserSnapshot> userSnapshotRedisTemplate(
            RedisConnectionFactory redisConnectionFactory,
            ObjectMapper objectMapper
    ) {
        // UserSnapshot 캐시 전용 RedisTemplate 객체를 생성한다.
        RedisTemplate<String, UserSnapshot> redisTemplate = new RedisTemplate<>();

        // Spring Boot가 Redis 설정을 기반으로 만들어둔 Redis 연결 정보를 주입한다.
        // 이 설정이 있어야 RedisTemplate이 실제 Redis 서버와 통신할 수 있다.
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        // Redis key를 문자열로 저장하기 위한 serializer이다.
        // 예: user:snapshot:{keycloakSub}
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Redis value를 JSON으로 저장하기 위한 serializer이다.
        // UserSnapshot 객체를 Redis에 저장할 때 JSON으로 변환하고,
        // Redis에서 읽어올 때 다시 UserSnapshot 객체로 변환한다.
        GenericJacksonJsonRedisSerializer jsonSerializer =
                new GenericJacksonJsonRedisSerializer(objectMapper);

        // 일반 key의 serializer를 문자열 방식으로 설정한다.
        redisTemplate.setKeySerializer(stringSerializer);

        // hash 구조를 사용할 경우 hash key도 문자열 방식으로 설정한다.
        redisTemplate.setHashKeySerializer(stringSerializer);

        // 일반 value의 serializer를 JSON 방식으로 설정한다.
        redisTemplate.setValueSerializer(jsonSerializer);

        // hash 구조를 사용할 경우 hash value도 JSON 방식으로 설정한다.
        redisTemplate.setHashValueSerializer(jsonSerializer);

        // 위에서 설정한 connectionFactory와 serializer 설정을 RedisTemplate에 최종 반영한다.
        redisTemplate.afterPropertiesSet();

        // UserSnapshot 캐시에서 사용할 RedisTemplate Bean을 반환한다.
        return redisTemplate;
    }

    /*
     Redis 기반 UserSnapshot 캐시 adapter를 등록한다.

     이 adapter는 다음 두 포트를 구현한다.

     - LoadUserSnapshotCachePort(조회)
     - SaveUserSnapshotCachePort(저장)

     RedisTemplate이 존재할 때만 등록된다.
     */
    @Bean
    @ConditionalOnBean(name = "userSnapshotRedisTemplate")
    @ConditionalOnMissingBean({
            LoadUserSnapshotCachePort.class,
            SaveUserSnapshotCachePort.class
    })
    public RedisUserSnapshotCacheAdapter redisUserSnapshotCacheAdapter(
            @Qualifier("userSnapshotRedisTemplate") RedisTemplate<String, UserSnapshot> redisTemplate,
            BbdSecurityProperties properties
    ) {
        return new RedisUserSnapshotCacheAdapter(redisTemplate, properties);
    }

    /*
User Service 호출용 RestClient를 등록한다.

bbd.security.user-service-base-url 값이 있을 때만 등록된다.

예:
bbd.security.user-service-base-url=http://bbd-user:8080
*/
    @Bean(name = "userServiceRestClient")
    @ConditionalOnProperty(
            prefix = "bbd.security",
            name = "user-service-base-url"
    )
    @ConditionalOnMissingBean(name = "userServiceRestClient")
    public RestClient userServiceRestClient(BbdSecurityProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getUserServiceBaseUrl())
                .build();
    }

    /*
     User Service에서 UserSnapshot을 조회하는 HTTP adapter를 등록한다.

     이 adapter는 LoadUserSnapshotPort를 구현한다.
     Redis 캐시에 UserSnapshot이 없을 때 User Service를 호출한다.
     */
    @Bean
    @ConditionalOnBean(name = "userServiceRestClient")
    @ConditionalOnMissingBean(LoadUserSnapshotPort.class)
    public UserServiceSnapshotAdapter userServiceSnapshotAdapter(
            @Qualifier("userServiceRestClient") RestClient restClient,
            BbdSecurityProperties properties
    ) {
        return new UserServiceSnapshotAdapter(restClient, properties);
    }


    /*
     현재 요청 사용자의 UserSnapshot을 조회하는 유스케이스를 등록한다.

     내부적으로 다음 순서로 동작한다.

     1. 현재 인증 사용자의 keycloakSub 추출
     2. Redis 캐시 조회
     3. Redis miss 시 User Service 호출
     4. 조회 결과 Redis 캐시 저장
     5. CurrentUserSnapshotResult 반환

     User Service HTTP adapter가 등록되어 있으면
     Redis 캐시 miss 시 User Service에서 UserSnapshot을 조회한다.

     bbd.security.user-service-base-url 설정이 없으면
     LoadUserSnapshotPort가 등록되지 않으므로
     이 유스케이스도 등록되지 않는다.
     */
    @Bean
    @ConditionalOnBean({
            ExtractAuthenticatedUserPort.class,
            LoadUserSnapshotCachePort.class,
            LoadUserSnapshotPort.class,
            SaveUserSnapshotCachePort.class
    })
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

    // ACTIVE, role, tenancy 같은 공통 인가 규칙을 검사하는 유스케이스를 등록한다.
    @Bean
    @ConditionalOnMissingBean(AuthorizeUserUseCase.class)
    public AuthorizeUserUseCase authorizeUserUseCase() {
        return new AuthorizeUserService();
    }


}
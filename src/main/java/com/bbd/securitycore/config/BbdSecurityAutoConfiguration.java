package com.bbd.securitycore.config;

import com.bbd.securitycore.adapter.in.aop.RoleAuthorizationAspect;
import com.bbd.securitycore.idempotency.IdempotencyAspect;
import com.bbd.securitycore.adapter.in.security.SpringSecurityAuthenticatedUserAdapter;
import com.bbd.securitycore.adapter.out.http.SecurityContextAccessTokenRelayInterceptor;
import com.bbd.securitycore.adapter.out.http.UserServiceSnapshotAdapter;
import com.bbd.securitycore.adapter.out.http.UserSnapshotHttpClient;
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
import com.bbd.securitycore.global.error.BbdSecurityExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;
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

// RedisConnectionFactory가 생기기 전에 security-core 자동설정이 먼저 평가돼서
// userSnapshotRedisTemplate이 안 뜨는 문제 해결
// Redis 자동설정 뒤에 실행되게 바꿈
@AutoConfiguration(after = DataRedisAutoConfiguration.class)
@ConditionalOnClass(SecurityFilterChain.class)
@EnableConfigurationProperties(BbdSecurityProperties.class)
// @RequireRole은 필터가 아니라 AOP라서 이 설정이 필요
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ImportHttpServices(
        group = BbdSecurityAutoConfiguration.USER_SERVICE_GROUP,
        types = UserSnapshotHttpClient.class
)
public class BbdSecurityAutoConfiguration {

    static final String USER_SERVICE_GROUP = "bbd-user-service";

    @Bean
    @ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
    @ConditionalOnMissingBean(BbdSecurityExceptionHandler.class)
    public BbdSecurityExceptionHandler bbdSecurityExceptionHandler() {
        return new BbdSecurityExceptionHandler();
    }

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
        // GenericJacksonJsonRedisSerializer였는데,
        // 그 방식은 Redis에서 읽을 때 UserSnapshot이 아니라 LinkedHashMap으로 역직렬화돼서 캐스팅 에러
        JacksonJsonRedisSerializer<UserSnapshot> jsonSerializer =
                new JacksonJsonRedisSerializer<>(objectMapper, UserSnapshot.class);

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
    HTTP Service Interface 기반 내부 호출에 Access Token Relay 설정을 적용한다.

    @ImportHttpServices로 등록되는 모든 HTTP Service group의
    RestClient.Builder에 interceptor를 추가한다.

    내부 HTTP 호출 시 현재 요청의 SecurityContext에서 Jwt access token을 꺼내
    Authorization: Bearer <access-token> 헤더로 전달한다.

    예:
    - Item Service -> Sales Service
    - Procurement Service -> Item Service
    - Inventory Service -> User Service
    - 각 MSA -> User Service Snapshot 조회

    즉, Gateway를 거치지 않는 MSA 내부 HTTP 통신에서도
    호출받는 MSA가 동일한 access token을 검증할 수 있게 한다.
    */
    @Bean
    @ConditionalOnMissingBean(name = "bbdAccessTokenRelayHttpServiceGroupConfigurer")
    public RestClientHttpServiceGroupConfigurer bbdAccessTokenRelayHttpServiceGroupConfigurer(
            BbdSecurityProperties properties,
            ObjectProvider<ClientHttpRequestFactoryBuilder<?>> requestFactoryBuilderProvider,
            ObjectProvider<HttpClientSettings> httpClientSettingsProvider
    ) {
        ClientHttpRequestFactoryBuilder<?> requestFactoryBuilder =
                requestFactoryBuilderProvider.getIfAvailable(ClientHttpRequestFactoryBuilder::detect);
        HttpClientSettings baseHttpClientSettings =
                httpClientSettingsProvider.getIfAvailable(HttpClientSettings::defaults);

        return groups -> {
            groups.forEachClient((group, clientBuilder) -> clientBuilder
                    .requestInterceptors(interceptors ->
                            interceptors.add(new SecurityContextAccessTokenRelayInterceptor())
                    )
            );

            groups.filterByName(USER_SERVICE_GROUP)
                    .forEachClient((group, clientBuilder) -> clientBuilder
                            .requestFactory(userServiceClientHttpRequestFactory(
                                    properties,
                                    requestFactoryBuilder,
                                    baseHttpClientSettings
                            ))
                    );
        };
    }

    private ClientHttpRequestFactory userServiceClientHttpRequestFactory(
            BbdSecurityProperties properties,
            ClientHttpRequestFactoryBuilder<?> requestFactoryBuilder,
            HttpClientSettings baseHttpClientSettings
    ) {
        BbdSecurityProperties.UserService userService = properties.getUserService();
        HttpClientSettings userServiceSettings = baseHttpClientSettings.withTimeouts(
                userService.getConnectTimeout(),
                userService.getReadTimeout()
        );

        return requestFactoryBuilder.build(userServiceSettings);
    }


    /*
   User Service에서 UserSnapshot을 조회하는 HTTP adapter를 등록한다.

   이 adapter는 LoadUserSnapshotPort를 구현한다.
   Redis 캐시에 UserSnapshot이 없을 때 User Service를 호출한다.

   UserSnapshotHttpClient는 @ImportHttpServices가 생성한 HTTP Service proxy Bean이다.
   */

    // @ConditionalOnBean(UserSnapshotHttpClient.class)
    // HTTP Service proxy Bean이 조건 평가 시점보다 늦게 등록돼서 UserServiceSnapshotAdapter가 안 만들어졌다.
    // 그래서 조건을 제거했다.
    @Bean
    @ConditionalOnMissingBean(LoadUserSnapshotPort.class)
    public UserServiceSnapshotAdapter userServiceSnapshotAdapter(
            UserSnapshotHttpClient userSnapshotHttpClient
    ) {
        return new UserServiceSnapshotAdapter(userSnapshotHttpClient);
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

     UserSnapshotHttpClient는 @ImportHttpServices를 통해 등록되는
     HTTP Service proxy Bean이다.

     각 MSA는 Spring HTTP Service group 설정에서
     bbd-user-service group의 base-url을 설정해야 한다.
     */
    @Bean
    @ConditionalOnBean({
            ExtractAuthenticatedUserPort.class,
            LoadUserSnapshotPort.class
    })

    // 캐시 포트가 반드시 있어야 GetCurrentUserSnapshotUseCase가 만들어졌는데
    // 이제 Redis가 없어도 User Service 직접 조회는 가능하게 바꿈
    @ConditionalOnMissingBean(GetCurrentUserSnapshotUseCase.class)
    public GetCurrentUserSnapshotUseCase getCurrentUserSnapshotUseCase(
            ExtractAuthenticatedUserPort extractAuthenticatedUserPort,
            ObjectProvider<LoadUserSnapshotCachePort> loadUserSnapshotCachePort,
            LoadUserSnapshotPort loadUserSnapshotPort,
            ObjectProvider<SaveUserSnapshotCachePort> saveUserSnapshotCachePort
    ) {
        return new GetCurrentUserSnapshotService(
                extractAuthenticatedUserPort,
                loadUserSnapshotCachePort.getIfAvailable(),
                loadUserSnapshotPort,
                saveUserSnapshotCachePort.getIfAvailable()
        );
    }

    // ACTIVE, role 같은 공통 인가 규칙을 검사하는 유스케이스를 등록한다.
    @Bean
    @ConditionalOnMissingBean(AuthorizeUserUseCase.class)
    public AuthorizeUserUseCase authorizeUserUseCase() {
        return new AuthorizeUserService();
    }

    /*
     @RequireRole 기반 접근 제어 Aspect를 등록한다.

     각 MSA는 Controller 또는 Service 클래스/메서드에 @RequireRole만 붙이면 되고,
     실제 UserSnapshot 조회와 role 검사는 이 Aspect가 수행한다.
     */
    @Bean
    @ConditionalOnBean(GetCurrentUserSnapshotUseCase.class)
    @ConditionalOnMissingBean(RoleAuthorizationAspect.class)
    public RoleAuthorizationAspect roleAuthorizationAspect(
            GetCurrentUserSnapshotUseCase getCurrentUserSnapshotUseCase,
            AuthorizeUserUseCase authorizeUserUseCase
    ) {
        return new RoleAuthorizationAspect(getCurrentUserSnapshotUseCase, authorizeUserUseCase);
    }

    /*
     @Idempotent 멱등 빠른길 Aspect 를 등록한다(공통 멱등 표준 — docs/idempotency-spec.md).

     각 MSA 는 변경 컨트롤러 메서드에 @Idempotent 만 붙이면 되고,
     실제 Idempotency-Key 헤더 처리·Redis 빠른길·409 변환은 이 Aspect 가 수행한다.

     StringRedisTemplate(=Redis 설정 존재) 이 있을 때만 등록된다. Redis 가 없으면 멱등 빠른길은 비활성(서비스의 DB UNIQUE 가 정확성 보루).
     serviceName 은 Redis 키 네임스페이스(idem:{service}:...)에 쓰인다.
     */
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(IdempotencyAspect.class)
    public IdempotencyAspect idempotencyAspect(
            StringRedisTemplate stringRedisTemplate,
            ExtractAuthenticatedUserPort extractAuthenticatedUserPort,
            @Value("${spring.application.name}") String serviceName
    ) {
        return new IdempotencyAspect(stringRedisTemplate, extractAuthenticatedUserPort, serviceName);
    }
}

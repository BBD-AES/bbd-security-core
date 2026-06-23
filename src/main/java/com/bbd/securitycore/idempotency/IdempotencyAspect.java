package com.bbd.securitycore.idempotency;

import com.bbd.securitycore.application.port.out.ExtractAuthenticatedUserPort;
import com.bbd.securitycore.global.error.ApiException;
import com.bbd.securitycore.global.error.dto.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/*
 @Idempotent 메서드에 멱등 빠른길을 적용하는 AOP(공통 멱등 표준 — docs/idempotency-spec.md).

 동작:
 - Idempotency-Key 헤더가 없으면 no-op(게이트웨이가 헤더를 강제하므로 라이브러리는 관대).
 - Redis 키 idem:{service}:{principal}:{key} 가 이미 있으면 409(이미 처리됨) — 순차 재요청 빠른길.
 - 정상 처리 후 키 기록(value="true", TTL 24h). 원본 응답은 캐시하지 않는다(표준).
 - 예외 시 키 미기록 → 정당한 재시도 가능.

 책임 경계:
 - 동시(같은 키 동시) 정확성은 각 서비스 UNIQUE(idempotency_key) 가 보장한다(spec §4). 본 Aspect 는 그 빠른길/규약 레이어.
 - 권장 부착 위치 = 컨트롤러 변경 메서드(트랜잭션 밖) → set 이 하위 @Transactional 커밋 후 성격을 갖는다. @Order(0) 로 가장 바깥에 둔다.
*/
@Aspect
@Order(0)
public class IdempotencyAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyAspect.class);
    private static final String HEADER = "Idempotency-Key";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ExtractAuthenticatedUserPort principalPort;
    private final String serviceName;

    public IdempotencyAspect(StringRedisTemplate redis, ExtractAuthenticatedUserPort principalPort, String serviceName) {
        this.redis = redis;
        this.principalPort = principalPort;
        this.serviceName = serviceName;
    }

    @Around("@annotation(com.bbd.securitycore.idempotency.Idempotent) || "
            + "@within(com.bbd.securitycore.idempotency.Idempotent)")
    public Object apply(ProceedingJoinPoint joinPoint) throws Throwable {
        String key = header();
        if (key == null || key.isBlank()) {
            return joinPoint.proceed(); // 헤더 없으면 멱등 미적용
        }

        String principal = principalPort.getCurrentKeycloakSub();
        if (principal == null) {
            // 인증 주체 없음. @Idempotent 는 인증된 변경 엔드포인트 전제 → 공유 네임스페이스로 묶지 않고 통과(오탐 방지).
            return joinPoint.proceed();
        }
        String redisKey = "idem:" + serviceName + ":" + principal + ":" + key;

        // Redis 는 최적화 레이어 — 장애 시 fail-open(가용성 의존성 안 됨). 정확성 보루는 서비스 DB UNIQUE(spec §4).
        if (existsQuietly(redisKey)) {
            throw new ApiException(ErrorCode.IDEMPOTENT_DUPLICATE); // 순차 재요청 → 409
        }

        Object result = joinPoint.proceed();
        // 커밋 후 성격: 정상 반환(=하위 @Transactional 커밋) 후 기록. value=true(응답 캐시 안 함).
        setQuietly(redisKey);
        return result;
    }

    // Redis 조회 실패는 빠른길 생략(중복 통과)으로 fail-open — 중복은 DB UNIQUE 가 막는다.
    private boolean existsQuietly(String redisKey) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(redisKey));
        } catch (RuntimeException e) {
            log.warn("멱등 Redis 조회 실패(fail-open, DB UNIQUE 로 위임): {}", e.toString());
            return false;
        }
    }

    // 기록 실패도 무시 — 다음 재요청은 DB UNIQUE 에 걸려 409.
    private void setQuietly(String redisKey) {
        try {
            redis.opsForValue().set(redisKey, "true", TTL);
        } catch (RuntimeException e) {
            log.warn("멱등 Redis 기록 실패(무시): {}", e.toString());
        }
    }

    private String header() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getHeader(HEADER);
        }
        return null;
    }
}

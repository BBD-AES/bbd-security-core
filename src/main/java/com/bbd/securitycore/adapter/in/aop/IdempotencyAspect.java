package com.bbd.securitycore.adapter.in.aop;

import com.bbd.securitycore.application.port.out.ExtractAuthenticatedUserPort;
import com.bbd.securitycore.global.error.ApiException;
import com.bbd.securitycore.global.error.dto.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
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

    @Around("@annotation(com.bbd.securitycore.adapter.in.annotation.Idempotent) || "
            + "@within(com.bbd.securitycore.adapter.in.annotation.Idempotent)")
    public Object apply(ProceedingJoinPoint joinPoint) throws Throwable {
        String key = header();
        if (key == null || key.isBlank()) {
            return joinPoint.proceed(); // 헤더 없으면 멱등 미적용
        }

        String redisKey = redisKey(key);
        if (Boolean.TRUE.equals(redis.hasKey(redisKey))) {
            throw new ApiException(ErrorCode.IDEMPOTENT_DUPLICATE); // 순차 재요청 → 409
        }

        Object result = joinPoint.proceed();
        // 커밋 후 성격: 정상 반환(=하위 @Transactional 커밋) 후 기록. value=true(응답 캐시 안 함).
        redis.opsForValue().set(redisKey, "true", TTL);
        return result;
    }

    private String redisKey(String key) {
        String principal = principalPort.getCurrentKeycloakSub();
        return "idem:" + serviceName + ":" + (principal == null ? "anon" : principal) + ":" + key;
    }

    private String header() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getHeader(HEADER);
        }
        return null;
    }
}

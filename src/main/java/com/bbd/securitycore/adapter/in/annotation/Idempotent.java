package com.bbd.securitycore.adapter.in.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
 멱등 처리를 선언하는 어노테이션(공통 멱등 표준 — docs/idempotency-spec.md).

 보통 변경(POST / 부수효과 있는 상태전이 PATCH) 컨트롤러 메서드에 붙인다.

 동작(IdempotencyAspect):
 - Idempotency-Key 헤더가 없으면 no-op(게이트웨이가 헤더 강제).
 - Redis 키 idem:{service}:{principal}:{key} 가 이미 있으면 409(이미 처리됨) — 순차 재요청 빠른길.
 - 정상 처리(=하위 @Transactional 커밋) 후 키 기록(value="true", TTL 24h). 응답은 캐시하지 않는다.

 ⚠ 동시(같은 키 동시 요청)의 정확성 보루는 각 서비스 테이블의 UNIQUE(idempotency_key) 다(spec §4).
   이 어노테이션은 그 위의 "순차 재요청 빠른길 + 규약(409·키 네임스페이스)" 레이어다.
   고가치 생성은 반드시 DB UNIQUE 를 함께 둘 것.
*/
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}

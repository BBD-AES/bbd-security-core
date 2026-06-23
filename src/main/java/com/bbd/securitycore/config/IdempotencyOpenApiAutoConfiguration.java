package com.bbd.securitycore.config;

import com.bbd.securitycore.idempotency.IdempotencyOpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springdoc.core.customizers.OperationCustomizer;

/*
 springdoc(Swagger)을 쓰는 소비 MSA 에서만 활성 — @Idempotent 엔드포인트에 Idempotency-Key 헤더를
 OpenAPI 문서로 노출한다. Redis 와 무관(헤더는 게이트웨이가 강제하므로 Redis 빠른길 유무와 별개로 문서엔 떠야 함).

 @ConditionalOnClass(OperationCustomizer) 로 springdoc 미사용 MSA 에선 자동 비활성(NoClassDefFoundError 없음).
*/
@AutoConfiguration
@ConditionalOnClass(OperationCustomizer.class)
public class IdempotencyOpenApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotencyOpenApiCustomizer.class)
    public IdempotencyOpenApiCustomizer idempotencyOpenApiCustomizer() {
        return new IdempotencyOpenApiCustomizer();
    }
}

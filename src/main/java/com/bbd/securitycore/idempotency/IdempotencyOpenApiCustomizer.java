package com.bbd.securitycore.idempotency;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

/*
 @Idempotent 가 붙은 엔드포인트에 Idempotency-Key 헤더 파라미터를 OpenAPI(Swagger) 문서에 추가한다.

 왜 필요한가:
 - IdempotencyAspect 는 헤더를 AOP(RequestContextHolder)로 읽으므로 컨트롤러 메서드에 @RequestHeader 가 없다.
   → springdoc 이 그 헤더의 존재를 알 수 없어 Swagger 에 입력칸이 안 생긴다.
 - 이 커스터마이저가 @Idempotent(메서드 또는 클래스)면 헤더 파라미터를 문서에 주입 → "어노테이션만으로" 스웨거에 키 입력칸이 뜬다.
 - 이미 @RequestHeader 로 같은 헤더를 선언한 경우(예: 본문 폴백 병행)는 중복 추가하지 않는다.
*/
public class IdempotencyOpenApiCustomizer implements OperationCustomizer {

    static final String HEADER = "Idempotency-Key";
    private static final String DESCRIPTION =
            "재시도·중복요청 방지 멱등 키(UUID 권장). 같은 키 재전송 시 409(이미 처리됨). "
                    + "게이트웨이가 변경 요청에 강제·전파하며, 서비스는 키가 있을 때 판정한다.";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        boolean idempotent = handlerMethod.hasMethodAnnotation(Idempotent.class)
                || handlerMethod.getBeanType().isAnnotationPresent(Idempotent.class);
        if (!idempotent) {
            return operation;
        }
        boolean alreadyDeclared = operation.getParameters() != null
                && operation.getParameters().stream().anyMatch(
                        p -> "header".equalsIgnoreCase(p.getIn()) && HEADER.equalsIgnoreCase(p.getName()));
        if (alreadyDeclared) {
            return operation;
        }
        operation.addParametersItem(new Parameter()
                .in("header")
                .name(HEADER)
                .required(false) // 서비스는 키 없으면 no-op(게이트웨이가 존재를 강제) — 문서상 optional
                .description(DESCRIPTION)
                .schema(new StringSchema().example("3fa85f64-5717-4562-b3fc-2c963f66afa6")));
        return operation;
    }
}

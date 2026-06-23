package com.bbd.securitycore.idempotency;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyOpenApiCustomizerTest {

    private final IdempotencyOpenApiCustomizer customizer = new IdempotencyOpenApiCustomizer();

    static class MethodAnnotatedController {
        @Idempotent
        @PostMapping("/create")
        public void create() { }

        @GetMapping("/list")
        public void list() { }
    }

    @Idempotent
    static class TypeAnnotatedController {
        @PostMapping("/create")
        public void create() { }
    }

    private HandlerMethod handler(Object bean, String method) throws NoSuchMethodException {
        Method m = bean.getClass().getMethod(method);
        return new HandlerMethod(bean, m);
    }

    private long idemHeaderCount(Operation op) {
        if (op.getParameters() == null) return 0;
        return op.getParameters().stream()
                .filter(p -> "header".equalsIgnoreCase(p.getIn())
                        && IdempotencyOpenApiCustomizer.HEADER.equalsIgnoreCase(p.getName()))
                .count();
    }

    @Test
    void addsHeader_whenMethodAnnotated() throws Exception {
        Operation op = new Operation();
        customizer.customize(op, handler(new MethodAnnotatedController(), "create"));
        assertEquals(1, idemHeaderCount(op));
        assertEquals(Boolean.FALSE, op.getParameters().get(0).getRequired());
    }

    @Test
    void addsHeader_whenClassAnnotated() throws Exception {
        Operation op = new Operation();
        customizer.customize(op, handler(new TypeAnnotatedController(), "create"));
        assertEquals(1, idemHeaderCount(op));
    }

    @Test
    void noHeader_whenNotAnnotated() throws Exception {
        Operation op = new Operation();
        customizer.customize(op, handler(new MethodAnnotatedController(), "list"));
        assertTrue(op.getParameters() == null || op.getParameters().isEmpty());
    }

    @Test
    void noDuplicate_whenAlreadyDeclaredViaRequestHeader() throws Exception {
        Operation op = new Operation();
        op.addParametersItem(new Parameter().in("header").name("Idempotency-Key"));
        customizer.customize(op, handler(new MethodAnnotatedController(), "create"));
        assertEquals(1, idemHeaderCount(op)); // 중복 추가 안 함
    }
}

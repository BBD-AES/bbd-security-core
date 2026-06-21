package com.bbd.securitycore.adapter.in.aop;

import com.bbd.securitycore.adapter.in.annotation.RequireRole;
import com.bbd.securitycore.application.model.CurrentUserSnapshotResult;
import com.bbd.securitycore.application.port.in.AuthorizeUserUseCase;
import com.bbd.securitycore.application.port.in.GetCurrentUserSnapshotUseCase;
import com.bbd.securitycore.domain.UserRole;
import com.bbd.securitycore.domain.UserSnapshot;
import com.bbd.securitycore.global.error.ApiException;
import com.bbd.securitycore.global.error.dto.ErrorCode;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

/*
 @RequireRole이 붙은 클래스 또는 메서드에 대해
 role 기반 접근 제어를 수행하는 AOP.

 이 Aspect가 하는 일:

 1. 현재 요청 사용자의 UserSnapshot 조회
 2. UserSnapshot 존재 여부 검사
 3. 사용자 상태 검사
 4. role 검사

 메서드에 @RequireRole이 있으면 메서드 설정을 우선 사용하고,
 없으면 클래스에 붙은 @RequireRole을 사용한다.
 */
@Aspect
public class RoleAuthorizationAspect {

    private final GetCurrentUserSnapshotUseCase getCurrentUserSnapshotUseCase;
    private final AuthorizeUserUseCase authorizeUserUseCase;

    public RoleAuthorizationAspect(
            GetCurrentUserSnapshotUseCase getCurrentUserSnapshotUseCase,
            AuthorizeUserUseCase authorizeUserUseCase
    ) {
        this.getCurrentUserSnapshotUseCase = getCurrentUserSnapshotUseCase;
        this.authorizeUserUseCase = authorizeUserUseCase;
    }

    /*
     @RequireRole이 메서드 또는 클래스에 붙은 경우
     실제 메서드 실행 전에 접근 권한을 검사한다.
     */
    @Before("@annotation(com.bbd.securitycore.adapter.in.annotation.RequireRole) || " +
            "@within(com.bbd.securitycore.adapter.in.annotation.RequireRole)")
    public void authorize(JoinPoint joinPoint) {
        RequireRole requireRole = findRequireRole(joinPoint);

        if (requireRole == null) {
            return;
        }

        CurrentUserSnapshotResult result =
                getCurrentUserSnapshotUseCase.getCurrentUserSnapshot();

        UserSnapshot userSnapshot = result == null ? null : result.toDomain();

        authorizeUserUseCase.requireActive(userSnapshot);
        validateRoles(userSnapshot, requireRole.value());
    }

    /*
     접근 제어 어노테이션을 찾는다.

     우선순위:
     1. 메서드에 붙은 @RequireRole
     2. 클래스에 붙은 @RequireRole

     AOP proxy 환경에서도 실제 구현 메서드의 어노테이션을 찾기 위해
     AopUtils.getMostSpecificMethod를 사용한다.
     */
    private RequireRole findRequireRole(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method interfaceMethod = signature.getMethod();

        Class<?> targetClass = joinPoint.getTarget() != null
                ? joinPoint.getTarget().getClass()
                : signature.getDeclaringType();

        Method targetMethod = AopUtils.getMostSpecificMethod(interfaceMethod, targetClass);

        RequireRole methodAnnotation =
                AnnotatedElementUtils.findMergedAnnotation(targetMethod, RequireRole.class);

        if (methodAnnotation != null) {
            return methodAnnotation;
        }

        return AnnotatedElementUtils.findMergedAnnotation(targetClass, RequireRole.class);
    }

    /*
     지정된 role 중 하나라도 일치하면 통과한다.
     */
    private void validateRoles(UserSnapshot userSnapshot, UserRole[] requiredRoles) {
        if (requiredRoles == null || requiredRoles.length == 0) {
            throw new ApiException(ErrorCode.FORBIDDEN_ROLE);
        }

        boolean matched = Arrays.stream(requiredRoles)
                .anyMatch(userSnapshot::hasRole);

        if (!matched) {
            throw new ApiException(ErrorCode.FORBIDDEN_ROLE);
        }
    }
}

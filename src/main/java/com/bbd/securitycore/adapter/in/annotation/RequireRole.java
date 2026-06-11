package com.bbd.securitycore.adapter.in.annotation;

import com.bbd.securitycore.domain.UserRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
 role 기반 접근 제어를 선언하는 어노테이션.

 클래스 또는 메서드에 붙일 수 있다.

 사용 예:

 @RequireRole({UserRole.HQ_MANAGER})

 @RequireRole({
     UserRole.HQ_MANAGER,
     UserRole.HQ_STAFF
 })

 의미:
 - 지정된 role 중 하나라도 일치하면 통과
 - 사용자의 status는 항상 ACTIVE여야 통과
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /*
     허용할 사용자 role 목록.
     */
    UserRole[] value();
}
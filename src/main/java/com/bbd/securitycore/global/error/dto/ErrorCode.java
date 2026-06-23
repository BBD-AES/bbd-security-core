package com.bbd.securitycore.global.error.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 인증/인가 에러
    AUTH_UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "AUTH001", "인증이 필요합니다."),

    // UserSnapshot 기반 인가 에러
    USER_SNAPSHOT_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH004", "사용자 스냅샷을 찾을 수 없습니다."),
    USER_INACTIVE(HttpStatus.FORBIDDEN, "AUTH005", "비활성화된 사용자입니다."),
    USER_PENDING(HttpStatus.FORBIDDEN, "AUTH006", "승인 대기 중인 사용자입니다."),
    FORBIDDEN_ROLE(HttpStatus.FORBIDDEN, "AUTH007", "필요한 역할이 없습니다."),
    USER_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AUTH503", "사용자 서비스를 일시적으로 사용할 수 없습니다."),

    // 멱등(idempotency) — 공통 멱등 표준(docs/idempotency-spec.md)
    IDEMPOTENT_DUPLICATE(HttpStatus.CONFLICT, "IDEM409", "이미 처리된 요청입니다. (멱등 중복 차단)");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

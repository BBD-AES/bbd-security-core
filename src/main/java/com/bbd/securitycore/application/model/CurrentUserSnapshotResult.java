package com.bbd.securitycore.application.model;

import com.bbd.securitycore.domain.TenancyType;
import com.bbd.securitycore.domain.UserRole;
import com.bbd.securitycore.domain.UserSnapshot;
import com.bbd.securitycore.domain.UserStatus;

/*
 현재 요청 사용자의 인가 판단에 필요한 사용자 스냅샷 결과 모델.

 이 객체는 application 계층에서 사용하는 결과 모델이며,
 외부 adapter나 응답 DTO에 도메인 모델 UserSnapshot을 직접 노출하지 않기 위한 중간 모델이다.
 */
public record CurrentUserSnapshotResult(
        Long userId,
        String keycloakSub,
        String employeeNumber,
        String username,
        String displayName,
        String email,
        String position,
        UserStatus status,
        UserRole role,
        TenancyType tenancyType,
        Long tenancyId,
        String tenancyName,
        Long version
) {

    // 도메인 모델 UserSnapshot을 application 결과 모델로 변환한다.
    public static CurrentUserSnapshotResult from(UserSnapshot snapshot) {
        return new CurrentUserSnapshotResult(
                snapshot.userId(),
                snapshot.keycloakSub(),
                snapshot.employeeNumber(),
                snapshot.username(),
                snapshot.displayName(),
                snapshot.email(),
                snapshot.position(),
                snapshot.status(),
                snapshot.role(),
                snapshot.tenancyType(),
                snapshot.tenancyId(),
                snapshot.tenancyName(),
                snapshot.version()
        );
    }

    // 인가 서비스에서 다시 도메인 규칙을 사용할 수 있도록 application 결과 모델을 UserSnapshot 도메인 모델로 변환한다.
    public UserSnapshot toDomain() {
        return new UserSnapshot(
                userId,
                keycloakSub,
                employeeNumber,
                username,
                displayName,
                email,
                position,
                status,
                role,
                tenancyType,
                tenancyId,
                tenancyName,
                version
        );
    }
}
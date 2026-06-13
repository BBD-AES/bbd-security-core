package com.bbd.securitycore.domain;

public record UserSnapshot(
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
        String tenancyName,
        Long version
) {

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isPending() {
        return status == UserStatus.PENDING;
    }

    public boolean hasRole(UserRole requiredRole) {
        // ERP 시스템 관리자는 모든 업무 role의 권한을 포함한다.
        return role == UserRole.ADMIN || role == requiredRole;
    }
}

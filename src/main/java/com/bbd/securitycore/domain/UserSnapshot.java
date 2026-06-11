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
        return role == requiredRole;
    }
}
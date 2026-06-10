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
        Long tenancyId,
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

    public boolean belongsToHq() {
        return tenancyType == TenancyType.HQ;
    }

    public boolean belongsToBranch() {
        return tenancyType == TenancyType.BRANCH;
    }

    public boolean belongsToTenancy(Long targetTenancyId) {
        return tenancyId != null && tenancyId.equals(targetTenancyId);
    }

    public boolean canAccessTenancy(Long targetTenancyId) {
        if (belongsToHq()) {
            return true;
        }

        return belongsToTenancy(targetTenancyId);
    }
}
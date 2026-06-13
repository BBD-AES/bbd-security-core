package com.bbd.securitycore.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UserSnapshotTest {

    @Test
    void adminIncludesEveryBusinessRole() {
        UserSnapshot admin = new UserSnapshot(
                1L,
                "admin-sub",
                "ADMIN",
                "admin",
                "관리자",
                "admin@example.com",
                "시스템관리자",
                UserStatus.ACTIVE,
                UserRole.ADMIN,
                TenancyType.HQ,
                "본사",
                1L
        );

        assertTrue(admin.hasRole(UserRole.ADMIN));
        assertTrue(admin.hasRole(UserRole.HQ_MANAGER));
        assertTrue(admin.hasRole(UserRole.HQ_STAFF));
        assertTrue(admin.hasRole(UserRole.BRANCH_MANAGER));
        assertTrue(admin.hasRole(UserRole.BRANCH_STAFF));
    }
}

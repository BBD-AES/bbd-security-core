package com.bbd.securitycore.application.service;

import com.bbd.securitycore.domain.TenancyType;
import com.bbd.securitycore.domain.UserRole;
import com.bbd.securitycore.domain.UserSnapshot;
import com.bbd.securitycore.domain.UserStatus;
import com.bbd.securitycore.global.error.ApiException;
import com.bbd.securitycore.global.error.dto.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthorizeUserServiceTest {

    private final AuthorizeUserService authorizeUserService = new AuthorizeUserService();

    @Test
    void requireActiveThrowsWhenSnapshotIsNull() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> authorizeUserService.requireActive(null)
        );

        assertEquals(ErrorCode.USER_SNAPSHOT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void requireActiveThrowsWhenUserIsPending() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> authorizeUserService.requireActive(snapshot(UserStatus.PENDING))
        );

        assertEquals(ErrorCode.USER_PENDING, exception.getErrorCode());
    }

    @Test
    void requireActiveThrowsWhenUserIsInactive() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> authorizeUserService.requireActive(snapshot(UserStatus.INACTIVE))
        );

        assertEquals(ErrorCode.USER_INACTIVE, exception.getErrorCode());
    }

    @Test
    void requireActiveAllowsActiveUser() {
        assertDoesNotThrow(() -> authorizeUserService.requireActive(snapshot(UserStatus.ACTIVE)));
    }

    private UserSnapshot snapshot(UserStatus status) {
        return new UserSnapshot(
                1L,
                "user-sub",
                "EMP001",
                "User",
                "user@example.com",
                "Staff",
                status,
                UserRole.HQ_STAFF,
                TenancyType.HQ,
                "HQ",
                1L
        );
    }
}

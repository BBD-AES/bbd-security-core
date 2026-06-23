package com.bbd.securitycore.adapter.in.aop;

import com.bbd.securitycore.adapter.in.annotation.RequireRole;
import com.bbd.securitycore.application.model.CurrentUserSnapshotResult;
import com.bbd.securitycore.application.service.AuthorizeUserService;
import com.bbd.securitycore.domain.TenancyType;
import com.bbd.securitycore.domain.UserRole;
import com.bbd.securitycore.domain.UserSnapshot;
import com.bbd.securitycore.domain.UserStatus;
import com.bbd.securitycore.global.error.ApiException;
import com.bbd.securitycore.global.error.dto.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleAuthorizationAspectTest {

    @Test
    void methodAnnotationOverridesClassAnnotation() {
        SecuredController controller = proxy(
                new SecuredController(),
                snapshotResult(UserRole.HQ_STAFF, UserStatus.ACTIVE)
        );

        assertDoesNotThrow(controller::methodRequiresStaff);
    }

    @Test
    void classAnnotationAppliesWhenMethodHasNoAnnotation() {
        SecuredController controller = proxy(
                new SecuredController(),
                snapshotResult(UserRole.HQ_MANAGER, UserStatus.ACTIVE)
        );

        assertDoesNotThrow(controller::classSecuredOperation);
    }

    @Test
    void deniesWhenRoleDoesNotMatch() {
        SecuredController controller = proxy(
                new SecuredController(),
                snapshotResult(UserRole.BRANCH_STAFF, UserStatus.ACTIVE)
        );

        ApiException exception = assertThrows(
                ApiException.class,
                controller::classSecuredOperation
        );

        assertEquals(ErrorCode.FORBIDDEN_ROLE, exception.getErrorCode());
    }

    @Test
    void deniesWhenRequiredRolesAreEmpty() {
        EmptyRoleController controller = proxy(
                new EmptyRoleController(),
                snapshotResult(UserRole.HQ_MANAGER, UserStatus.ACTIVE)
        );

        ApiException exception = assertThrows(
                ApiException.class,
                controller::emptyRoleOperation
        );

        assertEquals(ErrorCode.FORBIDDEN_ROLE, exception.getErrorCode());
    }

    @Test
    void deniesPendingUserBeforeRoleCheck() {
        SecuredController controller = proxy(
                new SecuredController(),
                snapshotResult(UserRole.HQ_MANAGER, UserStatus.PENDING)
        );

        ApiException exception = assertThrows(
                ApiException.class,
                controller::classSecuredOperation
        );

        assertEquals(ErrorCode.USER_PENDING, exception.getErrorCode());
    }

    @Test
    void deniesInactiveUserBeforeRoleCheck() {
        SecuredController controller = proxy(
                new SecuredController(),
                snapshotResult(UserRole.HQ_MANAGER, UserStatus.INACTIVE)
        );

        ApiException exception = assertThrows(
                ApiException.class,
                controller::classSecuredOperation
        );

        assertEquals(ErrorCode.USER_INACTIVE, exception.getErrorCode());
    }

    @Test
    void deniesWhenCurrentUserSnapshotResultIsNull() {
        SecuredController controller = proxy(new SecuredController(), null);

        ApiException exception = assertThrows(
                ApiException.class,
                controller::classSecuredOperation
        );

        assertEquals(ErrorCode.USER_SNAPSHOT_NOT_FOUND, exception.getErrorCode());
    }

    private static <T> T proxy(T target, CurrentUserSnapshotResult result) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new RoleAuthorizationAspect(
                () -> result,
                new AuthorizeUserService()
        ));
        return factory.getProxy();
    }

    private static CurrentUserSnapshotResult snapshotResult(UserRole role, UserStatus status) {
        return CurrentUserSnapshotResult.from(snapshot(role, status));
    }

    private static UserSnapshot snapshot(UserRole role, UserStatus status) {
        return new UserSnapshot(
                1L,
                "user-sub",
                "EMP001",
                "User",
                "user@example.com",
                "Staff",
                status,
                role,
                TenancyType.HQ,
                "HQ",
                1L
        );
    }

    @RequireRole(UserRole.HQ_MANAGER)
    static class SecuredController {

        public void classSecuredOperation() {
        }

        @RequireRole(UserRole.HQ_STAFF)
        public void methodRequiresStaff() {
        }
    }

    static class EmptyRoleController {

        @RequireRole({})
        public void emptyRoleOperation() {
        }
    }
}

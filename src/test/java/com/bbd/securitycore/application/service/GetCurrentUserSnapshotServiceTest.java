package com.bbd.securitycore.application.service;

import com.bbd.securitycore.application.model.CurrentUserSnapshotResult;
import com.bbd.securitycore.application.port.out.ExtractAuthenticatedUserPort;
import com.bbd.securitycore.application.port.out.LoadUserSnapshotCachePort;
import com.bbd.securitycore.application.port.out.LoadUserSnapshotPort;
import com.bbd.securitycore.application.port.out.SaveUserSnapshotCachePort;
import com.bbd.securitycore.domain.TenancyType;
import com.bbd.securitycore.domain.UserRole;
import com.bbd.securitycore.domain.UserSnapshot;
import com.bbd.securitycore.domain.UserStatus;
import com.bbd.securitycore.global.error.ApiException;
import com.bbd.securitycore.global.error.dto.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 UserSnapshot 캐시 장애 시 원본 User Service fallback 규칙을 검증한다.
 */
class GetCurrentUserSnapshotServiceTest {

    @Test
    void throwsUnauthenticatedWhenKeycloakSubIsBlank() {
        GetCurrentUserSnapshotService service = new GetCurrentUserSnapshotService(
                () -> " ",
                keycloakSub -> Optional.empty(),
                keycloakSub -> snapshot(keycloakSub),
                snapshot -> {
                }
        );

        ApiException exception = assertThrows(
                ApiException.class,
                service::getCurrentUserSnapshot
        );

        assertEquals(ErrorCode.AUTH_UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    void loadsFromUserServiceAndSavesCacheWhenCacheMisses() {
        RecordingUserSnapshotPort userServicePort =
                new RecordingUserSnapshotPort(snapshot("keycloak-sub"));
        RecordingSaveCachePort saveCachePort = new RecordingSaveCachePort(false);

        GetCurrentUserSnapshotService service = new GetCurrentUserSnapshotService(
                () -> "keycloak-sub",
                keycloakSub -> Optional.empty(),
                userServicePort,
                saveCachePort
        );

        CurrentUserSnapshotResult result = service.getCurrentUserSnapshot();

        assertTrue(userServicePort.called);
        assertTrue(saveCachePort.called);
        assertEquals("keycloak-sub", result.keycloakSub());
    }

    @Test
    void loadsFromUserServiceWhenCachePortIsMissing() {
        RecordingUserSnapshotPort userServicePort =
                new RecordingUserSnapshotPort(snapshot("keycloak-sub"));
        RecordingSaveCachePort saveCachePort = new RecordingSaveCachePort(false);

        GetCurrentUserSnapshotService service = new GetCurrentUserSnapshotService(
                () -> "keycloak-sub",
                null,
                userServicePort,
                saveCachePort
        );

        CurrentUserSnapshotResult result = service.getCurrentUserSnapshot();

        assertTrue(userServicePort.called);
        assertTrue(saveCachePort.called);
        assertEquals("keycloak-sub", result.keycloakSub());
    }

    @Test
    void throwsUserSnapshotNotFoundWhenUserServiceReturnsNull() {
        RecordingSaveCachePort saveCachePort = new RecordingSaveCachePort(false);

        GetCurrentUserSnapshotService service = new GetCurrentUserSnapshotService(
                () -> "keycloak-sub",
                keycloakSub -> Optional.empty(),
                keycloakSub -> null,
                saveCachePort
        );

        ApiException exception = assertThrows(
                ApiException.class,
                service::getCurrentUserSnapshot
        );

        assertEquals(ErrorCode.USER_SNAPSHOT_NOT_FOUND, exception.getErrorCode());
        assertTrue(saveCachePort.notFoundCalled);
        assertEquals("keycloak-sub", saveCachePort.notFoundKeycloakSub);
    }

    @Test
    void skipsUserServiceWhenNotFoundCacheHits() {
        NotFoundCachedPort cachePort = new NotFoundCachedPort();
        RecordingUserSnapshotPort userServicePort =
                new RecordingUserSnapshotPort(snapshot("keycloak-sub"));

        GetCurrentUserSnapshotService service = new GetCurrentUserSnapshotService(
                () -> "keycloak-sub",
                cachePort,
                userServicePort,
                snapshot -> {
                }
        );

        ApiException exception = assertThrows(
                ApiException.class,
                service::getCurrentUserSnapshot
        );

        assertEquals(ErrorCode.USER_SNAPSHOT_NOT_FOUND, exception.getErrorCode());
        assertTrue(cachePort.findCalled);
        assertTrue(cachePort.notFoundCalled);
        assertFalse(userServicePort.called);
    }

    @Test
    void fallsBackToUserServiceWhenCacheLookupFails() {
        FailingLoadCachePort cachePort = new FailingLoadCachePort();
        RecordingUserSnapshotPort userServicePort =
                new RecordingUserSnapshotPort(snapshot("keycloak-sub"));
        RecordingSaveCachePort saveCachePort = new RecordingSaveCachePort(false);

        GetCurrentUserSnapshotService service = new GetCurrentUserSnapshotService(
                () -> "keycloak-sub",
                cachePort,
                userServicePort,
                saveCachePort
        );

        CurrentUserSnapshotResult result = service.getCurrentUserSnapshot();

        assertTrue(cachePort.called);
        assertTrue(userServicePort.called);
        assertTrue(saveCachePort.called);
        assertEquals("keycloak-sub", result.keycloakSub());
    }

    @Test
    void returnsUserServiceSnapshotWhenCacheSaveFails() {
        RecordingUserSnapshotPort userServicePort =
                new RecordingUserSnapshotPort(snapshot("keycloak-sub"));
        RecordingSaveCachePort saveCachePort = new RecordingSaveCachePort(true);

        GetCurrentUserSnapshotService service = new GetCurrentUserSnapshotService(
                () -> "keycloak-sub",
                keycloakSub -> Optional.empty(),
                userServicePort,
                saveCachePort
        );

        CurrentUserSnapshotResult result = service.getCurrentUserSnapshot();

        assertTrue(userServicePort.called);
        assertTrue(saveCachePort.called);
        assertEquals("keycloak-sub", result.keycloakSub());
    }

    @Test
    void usesCachedSnapshotWithoutCallingUserService() {
        RecordingUserSnapshotPort userServicePort =
                new RecordingUserSnapshotPort(snapshot("keycloak-sub"));

        GetCurrentUserSnapshotService service = new GetCurrentUserSnapshotService(
                () -> "keycloak-sub",
                keycloakSub -> Optional.of(snapshot("keycloak-sub")),
                userServicePort,
                snapshot -> {
                }
        );

        CurrentUserSnapshotResult result = service.getCurrentUserSnapshot();

        assertFalse(userServicePort.called);
        assertEquals("keycloak-sub", result.keycloakSub());
    }

    private static UserSnapshot snapshot(String keycloakSub) {
        return new UserSnapshot(
                1L,
                keycloakSub,
                "EMP-001",
                "ERP User",
                "erp-user@example.com",
                "staff",
                UserStatus.ACTIVE,
                UserRole.HQ_STAFF,
                TenancyType.HQ,
                "본사",
                1L
        );
    }

    private static class FailingLoadCachePort implements LoadUserSnapshotCachePort {

        private boolean called;

        @Override
        public Optional<UserSnapshot> findByKeycloakSub(String keycloakSub) {
            called = true;
            throw new IllegalStateException("Redis unavailable");
        }
    }

    private static class NotFoundCachedPort implements LoadUserSnapshotCachePort {

        private boolean findCalled;
        private boolean notFoundCalled;

        @Override
        public Optional<UserSnapshot> findByKeycloakSub(String keycloakSub) {
            findCalled = true;
            return Optional.empty();
        }

        @Override
        public boolean isNotFoundCached(String keycloakSub) {
            notFoundCalled = true;
            return true;
        }
    }

    private static class RecordingUserSnapshotPort implements LoadUserSnapshotPort {

        private final UserSnapshot snapshot;
        private boolean called;

        private RecordingUserSnapshotPort(UserSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public UserSnapshot loadByKeycloakSub(String keycloakSub) {
            called = true;
            return snapshot;
        }
    }

    private static class RecordingSaveCachePort implements SaveUserSnapshotCachePort {

        private final boolean fail;
        private boolean called;
        private boolean notFoundCalled;
        private String notFoundKeycloakSub;

        private RecordingSaveCachePort(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void save(UserSnapshot snapshot) {
            called = true;
            if (fail) {
                throw new IllegalStateException("Redis unavailable");
            }
        }

        @Override
        public void saveNotFound(String keycloakSub) {
            notFoundCalled = true;
            notFoundKeycloakSub = keycloakSub;
            if (fail) {
                throw new IllegalStateException("Redis unavailable");
            }
        }
    }
}

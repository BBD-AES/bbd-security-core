package com.bbd.securitycore.adapter.out.http;

import com.bbd.securitycore.global.error.BbdSecurityException;
import com.bbd.securitycore.global.error.dto.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceSnapshotAdapterTest {

    @Test
    void returnsNullWhenUserServiceReturnsNotFound() {
        UserServiceSnapshotAdapter adapter = new UserServiceSnapshotAdapter(keycloakSub -> {
            throw HttpClientErrorException.create(
                    HttpStatus.NOT_FOUND,
                    "Not Found",
                    HttpHeaders.EMPTY,
                    new byte[0],
                    null
            );
        });

        assertNull(adapter.loadByKeycloakSub("keycloak-sub"));
    }

    @Test
    void mapsUserServiceServerErrorToServiceUnavailable() {
        UserServiceSnapshotAdapter adapter = new UserServiceSnapshotAdapter(keycloakSub -> {
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        });

        BbdSecurityException exception = assertThrows(
                BbdSecurityException.class,
                () -> adapter.loadByKeycloakSub("keycloak-sub")
        );

        assertEquals(ErrorCode.USER_SERVICE_UNAVAILABLE, exception.getErrorCode());
        assertInstanceOf(HttpServerErrorException.class, exception.getCause());
    }

    @Test
    void propagatesUserServiceClientErrorsExceptNotFound() {
        UserServiceSnapshotAdapter adapter = new UserServiceSnapshotAdapter(keycloakSub -> {
            throw HttpClientErrorException.create(
                    HttpStatus.BAD_REQUEST,
                    "Bad Request",
                    HttpHeaders.EMPTY,
                    new byte[0],
                    null
            );
        });

        assertThrows(
                HttpClientErrorException.class,
                () -> adapter.loadByKeycloakSub("keycloak-sub")
        );
    }

    @Test
    void mapsUserServiceTimeoutToServiceUnavailable() {
        UserServiceSnapshotAdapter adapter = new UserServiceSnapshotAdapter(keycloakSub -> {
            throw new ResourceAccessException("Read timed out");
        });

        BbdSecurityException exception = assertThrows(
                BbdSecurityException.class,
                () -> adapter.loadByKeycloakSub("keycloak-sub")
        );

        assertEquals(ErrorCode.USER_SERVICE_UNAVAILABLE, exception.getErrorCode());
        assertInstanceOf(ResourceAccessException.class, exception.getCause());
    }

    @Test
    void mapsUserServiceRestClientFailuresToServiceUnavailable() {
        UserServiceSnapshotAdapter adapter = new UserServiceSnapshotAdapter(keycloakSub -> {
            throw new RestClientException("Invalid response body");
        });

        BbdSecurityException exception = assertThrows(
                BbdSecurityException.class,
                () -> adapter.loadByKeycloakSub("keycloak-sub")
        );

        assertEquals(ErrorCode.USER_SERVICE_UNAVAILABLE, exception.getErrorCode());
        assertInstanceOf(RestClientException.class, exception.getCause());
    }
}

package com.bbd.securitycore.global.error;

import com.bbd.securitycore.global.error.dto.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BbdSecurityExceptionHandlerTest {

    private final BbdSecurityExceptionHandler handler = new BbdSecurityExceptionHandler();

    @Test
    void handlesApiExceptionWithExistingProblemDetailBody() {
        ResponseEntity<ProblemDetail> response =
                handler.handleApiException(new ApiException(ErrorCode.USER_PENDING));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertProblemDetail(response.getBody(), ErrorCode.USER_PENDING);
    }

    @Test
    void handlesBbdSecurityExceptionWithExistingProblemDetailBody() {
        ResponseEntity<ProblemDetail> response =
                handler.handleBbdSecurityException(new BbdSecurityException(ErrorCode.USER_SERVICE_UNAVAILABLE));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertProblemDetail(response.getBody(), ErrorCode.USER_SERVICE_UNAVAILABLE);
    }

    private void assertProblemDetail(ProblemDetail body, ErrorCode errorCode) {
        assertNotNull(body);
        assertEquals(errorCode.getCode(), body.getTitle());
        assertEquals(errorCode.getMessage(), body.getDetail());
        assertNotNull(body.getProperties());
        assertNotNull(body.getProperties().get("timestamp"));
    }
}

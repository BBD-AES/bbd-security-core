package com.bbd.securitycore.global.error;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class BbdSecurityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException exception) {
        return toResponse(exception);
    }

    @ExceptionHandler(BbdSecurityException.class)
    public ResponseEntity<ProblemDetail> handleBbdSecurityException(BbdSecurityException exception) {
        return toResponse(exception);
    }

    private ResponseEntity<ProblemDetail> toResponse(ErrorResponseException exception) {
        return ResponseEntity
                .status(exception.getStatusCode())
                .body(exception.getBody());
    }
}

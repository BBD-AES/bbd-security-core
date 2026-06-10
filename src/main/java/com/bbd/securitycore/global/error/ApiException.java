package com.bbd.securitycore.global.error;

import com.bbd.securitycore.global.error.dto.ErrorCode;
import lombok.Getter;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.time.OffsetDateTime;

@Getter
public class ApiException extends ErrorResponseException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getStatus(), createBody(errorCode), null);
        this.errorCode = errorCode;
    }

    private static ProblemDetail createBody(ErrorCode errorCode) {
        ProblemDetail body = ProblemDetail.forStatus(errorCode.getStatus());
        body.setProperty("timestamp", OffsetDateTime.now());
        body.setTitle(errorCode.getCode());
        body.setDetail(errorCode.getMessage());
        return body;
    }
}
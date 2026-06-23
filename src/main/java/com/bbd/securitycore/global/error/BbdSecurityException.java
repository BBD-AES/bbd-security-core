package com.bbd.securitycore.global.error;

import com.bbd.securitycore.global.error.dto.ErrorCode;
import lombok.Getter;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.time.OffsetDateTime;

/*
 bbd-security-core가 직접 발생시키는 프레임워크 소유 예외.

 각 MSA의 ApiException 타입에 의존하지 않고, Spring의 ErrorResponseException
 계약으로 HTTP status와 ProblemDetail 응답 의미를 보존한다.
 */
@Getter
public class BbdSecurityException extends ErrorResponseException {

    private final ErrorCode errorCode;

    public BbdSecurityException(ErrorCode errorCode) {
        super(errorCode.getStatus(), createBody(errorCode), null);
        this.errorCode = errorCode;
    }

    public BbdSecurityException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getStatus(), createBody(errorCode), cause);
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

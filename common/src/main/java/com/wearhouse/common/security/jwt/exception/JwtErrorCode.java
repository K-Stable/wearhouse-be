package com.wearhouse.common.security.jwt.exception;

import com.wearhouse.common.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum JwtErrorCode implements ErrorCode {
    ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "JWT_401_001", "유효하지 않은 access token 입니다."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "JWT_401_002", "유효하지 않은 refresh token 입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "JWT_403_001", "접근 권한이 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    JwtErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}

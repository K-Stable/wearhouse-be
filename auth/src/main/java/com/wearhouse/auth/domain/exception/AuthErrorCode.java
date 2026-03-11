package com.wearhouse.auth.domain.exception;

import com.wearhouse.common.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH_409_001", "이미 가입된 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_401_001", "로그인 아이디 또는 비밀번호가 올바르지 않습니다."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_401_002", "유효하지 않은 refresh token 입니다."),
    USER_NOT_ACTIVE(HttpStatus.FORBIDDEN, "AUTH_403_001", "비활성화된 사용자입니다."),
    INTERNAL_SECRET_INVALID(HttpStatus.FORBIDDEN, "AUTH_403_002", "내부 인증 헤더가 유효하지 않습니다."),
    ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_401_003", "유효하지 않은 access token 입니다."),
    USER_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_503_001", "user-service 호출에 실패했습니다."),
    USER_SERVICE_INVALID_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH_500_001", "user-service 응답이 유효하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    AuthErrorCode(HttpStatus status, String code, String message) {
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

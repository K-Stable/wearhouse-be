package com.wearhouse.user.domain.exception;

import com.wearhouse.common.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER_409_001", "이미 가입된 이메일입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404_001", "사용자를 찾을 수 없습니다."),
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404_002", "배송지를 찾을 수 없습니다."),
    INTERNAL_SECRET_INVALID(HttpStatus.FORBIDDEN, "USER_403_001", "내부 인증 헤더가 유효하지 않습니다."),
    USER_TYPE_INVALID(HttpStatus.BAD_REQUEST, "USER_400_001", "유효하지 않은 userType 입니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "USER_400_002", "현재 비밀번호가 일치하지 않습니다."),
    ORDER_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "USER_503_001", "order-service 호출에 실패했습니다."),
    ORDER_SERVICE_INVALID_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR, "USER_500_001", "order-service 응답이 유효하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    UserErrorCode(HttpStatus status, String code, String message) {
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

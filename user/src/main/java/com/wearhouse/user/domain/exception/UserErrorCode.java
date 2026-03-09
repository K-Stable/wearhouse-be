package com.wearhouse.user.domain.exception;

import com.wearhouse.common.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {
    LOGIN_ID_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER_409_000", "이미 사용 중인 로그인 아이디입니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER_409_001", "이미 가입된 이메일입니다."),
    LOGIN_ID_OR_EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER_409_002", "이미 사용 중인 로그인 아이디 또는 이메일입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404_001", "사용자를 찾을 수 없습니다."),
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404_002", "배송지를 찾을 수 없습니다."),
    INTERNAL_SECRET_INVALID(HttpStatus.FORBIDDEN, "USER_403_001", "내부 인증 헤더가 유효하지 않습니다."),
    USER_TYPE_INVALID(HttpStatus.BAD_REQUEST, "USER_400_001", "유효하지 않은 userType 입니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "USER_400_002", "현재 비밀번호가 일치하지 않습니다."),
    SIGNUP_PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "USER_400_003", "비밀번호 확인이 일치하지 않습니다."),
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "USER_400_004", "새 비밀번호 확인이 일치하지 않습니다."),
    PASSWORD_SAME_AS_CURRENT(HttpStatus.BAD_REQUEST, "USER_400_005", "새 비밀번호는 현재 비밀번호와 달라야 합니다."),
    EMAIL_VERIFICATION_REQUIRED(HttpStatus.BAD_REQUEST, "USER_400_006", "이메일 인증이 필요합니다."),
    EMAIL_VERIFICATION_CODE_INVALID(HttpStatus.BAD_REQUEST, "USER_400_007", "이메일 인증 코드가 유효하지 않습니다."),
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

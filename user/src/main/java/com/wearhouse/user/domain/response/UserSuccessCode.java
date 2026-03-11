package com.wearhouse.user.domain.response;

import com.wearhouse.common.global.response.SuccessCode;
import org.springframework.http.HttpStatus;

public enum UserSuccessCode implements SuccessCode {
    BUYER_LOGIN_ID_AVAILABILITY_CHECKED(HttpStatus.OK, "USER_200_001", "구매자 로그인 아이디 중복 확인에 성공했습니다."),
    BUYER_EMAIL_CODE_SENT(HttpStatus.OK, "USER_200_002", "구매자 이메일 인증 코드 전송에 성공했습니다."),
    BUYER_EMAIL_CODE_VERIFIED(HttpStatus.OK, "USER_200_003", "구매자 이메일 인증 확인에 성공했습니다."),
    BUYER_SIGNUP_COMPLETED(HttpStatus.CREATED, "USER_201_001", "구매자 회원가입이 완료되었습니다."),
    SELLER_LOGIN_ID_AVAILABILITY_CHECKED(HttpStatus.OK, "USER_200_011", "판매자 로그인 아이디 중복 확인에 성공했습니다."),
    SELLER_EMAIL_CODE_SENT(HttpStatus.OK, "USER_200_012", "판매자 이메일 인증 코드 전송에 성공했습니다."),
    SELLER_EMAIL_CODE_VERIFIED(HttpStatus.OK, "USER_200_013", "판매자 이메일 인증 확인에 성공했습니다."),
    SELLER_SIGNUP_COMPLETED(HttpStatus.CREATED, "USER_201_011", "판매자 회원가입이 완료되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    UserSuccessCode(HttpStatus status, String code, String message) {
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

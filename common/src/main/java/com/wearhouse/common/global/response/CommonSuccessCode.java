package com.wearhouse.common.global.response;

import org.springframework.http.HttpStatus;

public enum CommonSuccessCode implements SuccessCode {
    SUCCESS(HttpStatus.OK, "SUCCESS", "요청이 성공적으로 처리되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    CommonSuccessCode(HttpStatus status, String code, String message) {
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

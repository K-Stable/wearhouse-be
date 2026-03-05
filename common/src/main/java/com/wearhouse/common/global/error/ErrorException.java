package com.wearhouse.common.global.error;

public class ErrorException extends RuntimeException {

    private final ErrorCode errorCode;

    public ErrorException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ErrorException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}

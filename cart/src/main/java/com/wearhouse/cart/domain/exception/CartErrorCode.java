package com.wearhouse.cart.domain.exception;

import com.wearhouse.common.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CartErrorCode implements ErrorCode {
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CART_404_001", "장바구니 상품을 찾을 수 없습니다."),
    FORBIDDEN_CART_ACCESS(HttpStatus.FORBIDDEN, "CART_403_001", "장바구니 접근 권한이 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    CartErrorCode(HttpStatus status, String code, String message) {
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

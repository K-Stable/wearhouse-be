package com.wearhouse.cart.domain.response;

import com.wearhouse.common.global.response.SuccessCode;
import org.springframework.http.HttpStatus;

public enum CartSuccessCode implements SuccessCode {
    CART_ITEM_LIST_FETCHED(HttpStatus.OK, "CART_200_001", "장바구니 목록 조회에 성공했습니다."),
    CART_ITEM_UPSERTED(HttpStatus.OK, "CART_200_002", "장바구니 상품이 저장되었습니다."),
    CART_ITEM_QUANTITY_UPDATED(HttpStatus.OK, "CART_200_003", "장바구니 수량이 변경되었습니다."),
    CART_ITEM_DELETED(HttpStatus.OK, "CART_200_004", "장바구니 상품이 삭제되었습니다."),
    CART_ITEMS_CLEARED(HttpStatus.OK, "CART_200_005", "장바구니가 비워졌습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    CartSuccessCode(HttpStatus status, String code, String message) {
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

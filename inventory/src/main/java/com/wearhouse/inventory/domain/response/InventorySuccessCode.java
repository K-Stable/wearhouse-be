package com.wearhouse.inventory.domain.response;

import com.wearhouse.common.global.response.SuccessCode;
import org.springframework.http.HttpStatus;




public enum InventorySuccessCode implements SuccessCode {
    INVENTORY_CREATED(HttpStatus.CREATED, "INVENTORY_201_001", "재고가 생성되었습니다."),
    INVENTORY_FETCHED(HttpStatus.OK, "INVENTORY_200_001", "재고를 조회했습니다."),
    INVENTORY_LIST_FETCHED(HttpStatus.OK, "INVENTORY_200_002", "재고 목록을 조회했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    InventorySuccessCode(HttpStatus status, String code, String message) {
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

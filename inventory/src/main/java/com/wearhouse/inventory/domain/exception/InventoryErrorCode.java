package com.wearhouse.inventory.domain.exception;

import com.wearhouse.common.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum InventoryErrorCode implements ErrorCode {
    INVALID_COMMAND(HttpStatus.BAD_REQUEST, "INVENTORY_400_001", "재고 커맨드가 올바르지 않습니다."),
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "INVENTORY_404_001", "재고 정보를 찾을 수 없습니다."),
    OUT_OF_STOCK(HttpStatus.CONFLICT, "INVENTORY_409_001", "재고가 부족합니다."),
    OPTIMISTIC_CONFLICT(HttpStatus.CONFLICT, "INVENTORY_409_002", "동시성 충돌이 발생했습니다."),
    HOT_SKU_LOCK_ACQUIRE_FAILED(HttpStatus.CONFLICT, "INVENTORY_409_003", "핫 SKU 락 획득에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    InventoryErrorCode(HttpStatus status, String code, String message) {
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

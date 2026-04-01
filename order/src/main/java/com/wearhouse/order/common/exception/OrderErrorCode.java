package com.wearhouse.order.common.exception;

import com.wearhouse.common.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum OrderErrorCode implements ErrorCode {
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_404_001", "주문 정보를 찾을 수 없습니다."),
    DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_404_002", "배송 정보를 찾을 수 없습니다."),
    FORBIDDEN_ORDER_ACCESS(HttpStatus.FORBIDDEN, "ORDER_403_001", "주문 접근 권한이 없습니다."),
    ORDER_ITEM_EMPTY(HttpStatus.BAD_REQUEST, "ORDER_400_001", "주문 상품은 최소 1개 이상이어야 합니다."),
    INVALID_ORDER_AMOUNT(HttpStatus.BAD_REQUEST, "ORDER_400_002", "주문 금액이 올바르지 않습니다."),
    INVALID_ORDER_STATE(HttpStatus.CONFLICT, "ORDER_409_001", "현재 주문 상태에서는 처리할 수 없습니다."),
    INVENTORY_OUT_OF_STOCK(HttpStatus.CONFLICT, "ORDER_409_002", "재고 부족으로 예약에 실패했습니다."),
    INVENTORY_LOCK_FAILED(HttpStatus.CONFLICT, "ORDER_409_003", "요청이 몰려 재고 예약에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    INVENTORY_RESERVE_FAILED(HttpStatus.CONFLICT, "ORDER_409_004", "재고 예약에 실패했습니다."),
    INVENTORY_PREVIEW_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "ORDER_503_001", "재고 프리뷰 조회에 실패했습니다."),
    USER_PREVIEW_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "ORDER_503_002", "회원 프리뷰 조회에 실패했습니다."),
    INVENTORY_PREVIEW_INVALID_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR, "ORDER_500_001", "재고 프리뷰 응답이 올바르지 않습니다."),
    USER_PREVIEW_INVALID_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR, "ORDER_500_002", "회원 프리뷰 응답이 올바르지 않습니다."),
    OUTBOX_PUBLISH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ORDER_500_003", "주문 이벤트 발행에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    OrderErrorCode(HttpStatus status, String code, String message) {
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

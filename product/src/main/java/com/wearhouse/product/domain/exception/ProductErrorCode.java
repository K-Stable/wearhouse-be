package com.wearhouse.product.domain.exception;

import com.wearhouse.common.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ProductErrorCode implements ErrorCode {
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_404_001", "상품을 찾을 수 없습니다."),
    PRODUCT_OPTION_REQUIRED(HttpStatus.BAD_REQUEST, "PRODUCT_400_001", "상품 옵션은 최소 1개 이상이어야 합니다."),
    PRODUCT_OPTION_INVALID(HttpStatus.BAD_REQUEST, "PRODUCT_400_002", "상품 옵션 정보가 올바르지 않습니다."),
    PRODUCT_IMAGE_INVALID(HttpStatus.BAD_REQUEST, "PRODUCT_400_003", "상품 이미지 정보가 올바르지 않습니다."),
    FORBIDDEN_PRODUCT_ACCESS(HttpStatus.FORBIDDEN, "PRODUCT_403_001", "해당 상품에 접근할 권한이 없습니다."),
    INVALID_PRODUCT_STATUS(HttpStatus.BAD_REQUEST, "PRODUCT_400_004", "상품 상태 값이 올바르지 않습니다."),
    INVALID_USER_TYPE(HttpStatus.FORBIDDEN, "PRODUCT_403_002", "허용되지 않은 사용자 타입입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ProductErrorCode(HttpStatus status, String code, String message) {
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

package com.wearhouse.product.domain.response;

import com.wearhouse.common.global.response.SuccessCode;
import org.springframework.http.HttpStatus;

public enum ProductSuccessCode implements SuccessCode {
    PRODUCT_SEASON_CREATED(HttpStatus.CREATED, "PRODUCT_201_002", "상품 시즌이 생성되었습니다."),
    PRODUCT_CREATED(HttpStatus.CREATED, "PRODUCT_201_001", "상품이 생성되었습니다."),
    PRODUCT_IMAGE_UPLOAD_URL_CREATED(HttpStatus.CREATED, "PRODUCT_201_003", "상품 이미지 업로드 URL 생성에 성공했습니다."),
    PRODUCT_SEASON_UPDATED(HttpStatus.OK, "PRODUCT_200_009", "상품 시즌이 수정되었습니다."),
    PRODUCT_SEASON_DELETED(HttpStatus.OK, "PRODUCT_200_010", "상품 시즌이 삭제되었습니다."),
    SELLER_PRODUCT_SEASON_LIST_FETCHED(HttpStatus.OK, "PRODUCT_200_007", "판매자 상품 시즌 목록 조회에 성공했습니다."),
    SELLER_PRODUCT_SEASON_FETCHED(HttpStatus.OK, "PRODUCT_200_011", "판매자 상품 시즌 조회에 성공했습니다."),
    SELLER_PRODUCT_LIST_FETCHED(HttpStatus.OK, "PRODUCT_200_001", "판매자 상품 목록 조회에 성공했습니다."),
    SELLER_PRODUCT_FETCHED(HttpStatus.OK, "PRODUCT_200_002", "판매자 상품 상세 조회에 성공했습니다."),
    PRODUCT_STATUS_UPDATED(HttpStatus.OK, "PRODUCT_200_003", "상품 상태가 변경되었습니다."),
    PRODUCT_DELETED(HttpStatus.OK, "PRODUCT_200_004", "상품이 삭제되었습니다."),
    BUYER_PRODUCT_SEASON_LIST_FETCHED(HttpStatus.OK, "PRODUCT_200_008", "구매자 상품 시즌 목록 조회에 성공했습니다."),
    BUYER_PRODUCT_LIST_FETCHED(HttpStatus.OK, "PRODUCT_200_005", "구매자 상품 목록 조회에 성공했습니다."),
    BUYER_PRODUCT_FETCHED(HttpStatus.OK, "PRODUCT_200_006", "구매자 상품 상세 조회에 성공했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ProductSuccessCode(HttpStatus status, String code, String message) {
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

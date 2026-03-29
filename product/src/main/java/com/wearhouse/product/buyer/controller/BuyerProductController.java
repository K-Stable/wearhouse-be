package com.wearhouse.product.buyer.controller;

import com.wearhouse.common.global.pagination.CursorPageResponse;
import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.product.domain.dto.response.BuyerProductDetailResponse;
import com.wearhouse.product.domain.dto.response.BuyerProductListResponse;
import com.wearhouse.product.domain.dto.response.ProductSeasonListResponse;
import com.wearhouse.product.domain.model.BuyerProductSortType;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.response.ProductSuccessCode;
import com.wearhouse.product.domain.service.buyer.BuyerProductQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/buyer/products")
@RequiredArgsConstructor
@Slf4j
public class BuyerProductController {

    private final BuyerProductQueryService buyerProductQueryService;


    @GetMapping
    public ApiResponse<CursorPageResponse<BuyerProductListResponse>> getBuyerProducts(
            @RequestParam(required = false) Category category,
            @RequestParam(required = false, defaultValue = "LATEST") BuyerProductSortType sort,
            @RequestParam(required = false, name = "cursorId") Long cursorId,
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        CursorPageResponse<BuyerProductListResponse> response =
                buyerProductQueryService.getBuyerProducts(category, sort, cursorId, limit);
        return ApiResponse.success(ProductSuccessCode.BUYER_PRODUCT_LIST_FETCHED, response);
    }

    @GetMapping("/seasons")
    public ApiResponse<CursorPageResponse<ProductSeasonListResponse>> getBuyerProductSeasons(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        CursorPageResponse<ProductSeasonListResponse> response = buyerProductQueryService.getBuyerSeasons(cursor, limit);
        return ApiResponse.success(ProductSuccessCode.BUYER_PRODUCT_SEASON_LIST_FETCHED, response);
    }

    @GetMapping("/{productId}")
    public ApiResponse<BuyerProductDetailResponse> getBuyerProductDetail(@PathVariable Long productId) {
        BuyerProductDetailResponse response = buyerProductQueryService.getBuyerProductDetail(productId);
        return ApiResponse.success(ProductSuccessCode.BUYER_PRODUCT_FETCHED, response);
    }
}

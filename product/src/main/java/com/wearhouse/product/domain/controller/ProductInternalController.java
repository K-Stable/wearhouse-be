package com.wearhouse.product.domain.controller;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.product.domain.dto.request.InternalProductSoldOutSyncRequest;
import com.wearhouse.product.domain.response.ProductSuccessCode;
import com.wearhouse.product.domain.service.seller.SellerProductCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/products")
public class ProductInternalController {

    private final SellerProductCommandService sellerProductCommandService;

    @PatchMapping("/status/sold-out")
    public ApiResponse<Void> markProductsSoldOut(
            @Valid @RequestBody InternalProductSoldOutSyncRequest request
    ) {
        sellerProductCommandService.markProductsSoldOut(request.productIds());
        return ApiResponse.success(ProductSuccessCode.PRODUCT_STATUS_UPDATED);
    }
}

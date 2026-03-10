package com.wearhouse.product.domain.controller;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.security.current.LoginSeller;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.product.domain.dto.request.ProductCreateRequest;
import com.wearhouse.product.domain.dto.request.ProductStatusesUpdateRequest;
import com.wearhouse.product.domain.dto.request.ProductStatusUpdateRequest;
import com.wearhouse.product.domain.dto.response.SellerProductListResponse;
import com.wearhouse.product.domain.dto.response.SellerProductResponse;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.response.ProductSuccessCode;
import com.wearhouse.product.domain.service.seller.SellerProductCommandService;
import com.wearhouse.product.domain.service.seller.SellerProductQueryService;
import jakarta.validation.Valid;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/products")
@RequiredArgsConstructor
@Slf4j
public class ProductSellerController {

    private final SellerProductCommandService sellerProductCommandService;
    private final SellerProductQueryService sellerProductQueryService;

    @PostMapping
    public ApiResponse<Void> createProduct(
            @LoginSeller LoginUser currentUser,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        sellerProductCommandService.createProduct(currentUser, request);
        return ApiResponse.success(ProductSuccessCode.PRODUCT_CREATED);
    }

    @GetMapping
    public ApiResponse<List<SellerProductListResponse>> getSellerProducts(
            @LoginSeller LoginUser currentUser,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit
    ) {
        List<SellerProductListResponse> response = sellerProductQueryService.getSellerProducts(currentUser, status, keyword, limit);
        return ApiResponse.success(ProductSuccessCode.SELLER_PRODUCT_LIST_FETCHED, response);
    }

    @GetMapping("/{productId}")
    public ApiResponse<SellerProductResponse> getSellerProduct(
            @LoginSeller LoginUser currentUser,
            @PathVariable Long productId
    ) {
        SellerProductResponse response = sellerProductQueryService.getSellerProduct(currentUser, productId);
        return ApiResponse.success(ProductSuccessCode.SELLER_PRODUCT_FETCHED, response);
    }

    @PatchMapping("/{productId}/status")
    public ApiResponse<Void> updateStatus(
            @LoginSeller LoginUser currentUser,
            @PathVariable Long productId,
            @Valid @RequestBody ProductStatusUpdateRequest request
    ) {
        sellerProductCommandService.updateProductStatus(currentUser, productId, request.status());
        return ApiResponse.success(ProductSuccessCode.PRODUCT_STATUS_UPDATED);
    }

    @PatchMapping("/statuses")
    public ApiResponse<Void> updateStatuses(
            @LoginSeller LoginUser currentUser,
            @Valid @RequestBody ProductStatusesUpdateRequest request
    ) {
        sellerProductCommandService.updateProductStatuses(currentUser, request.productIds(), request.status());
        return ApiResponse.success(ProductSuccessCode.PRODUCT_STATUS_UPDATED);
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(
            @LoginSeller LoginUser currentUser,
            @PathVariable Long productId
    ) {
        sellerProductCommandService.deleteProduct(currentUser, productId);
        return ApiResponse.success(ProductSuccessCode.PRODUCT_DELETED);
    }
}

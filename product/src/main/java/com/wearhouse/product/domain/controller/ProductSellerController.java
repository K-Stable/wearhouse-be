package com.wearhouse.product.domain.controller;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.security.current.CurrentUser;
import com.wearhouse.common.security.current.CurrentUserPrincipal;
import com.wearhouse.product.domain.dto.request.ProductCreateRequest;
import com.wearhouse.product.domain.dto.request.ProductStatusUpdateRequest;
import com.wearhouse.product.domain.dto.response.SellerProductListResponse;
import com.wearhouse.product.domain.dto.response.SellerProductResponse;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.response.ProductSuccessCode;
import com.wearhouse.product.domain.service.ProductCommandService;
import com.wearhouse.product.domain.service.ProductQueryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/products")
public class ProductSellerController {

    private final ProductCommandService productCommandService;
    private final ProductQueryService productQueryService;

    public ProductSellerController(ProductCommandService productCommandService, ProductQueryService productQueryService) {
        this.productCommandService = productCommandService;
        this.productQueryService = productQueryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SellerProductResponse> createProduct(
            @CurrentUser CurrentUserPrincipal currentUser,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        Long productId = productCommandService.createProduct(currentUser, request);
        SellerProductResponse response = productQueryService.getSellerProduct(currentUser, productId);
        return ApiResponse.success(ProductSuccessCode.PRODUCT_CREATED, response);
    }

    @GetMapping
    public ApiResponse<List<SellerProductListResponse>> getSellerProducts(
            @CurrentUser CurrentUserPrincipal currentUser,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit
    ) {
        List<SellerProductListResponse> response = productQueryService.getSellerProducts(currentUser, status, keyword, limit);
        return ApiResponse.success(ProductSuccessCode.SELLER_PRODUCT_LIST_FETCHED, response);
    }

    @GetMapping("/{productId}")
    public ApiResponse<SellerProductResponse> getSellerProduct(
            @CurrentUser CurrentUserPrincipal currentUser,
            @PathVariable Long productId
    ) {
        SellerProductResponse response = productQueryService.getSellerProduct(currentUser, productId);
        return ApiResponse.success(ProductSuccessCode.SELLER_PRODUCT_FETCHED, response);
    }

    @PatchMapping("/{productId}/status")
    public ApiResponse<SellerProductResponse> updateStatus(
            @CurrentUser CurrentUserPrincipal currentUser,
            @PathVariable Long productId,
            @Valid @RequestBody ProductStatusUpdateRequest request
    ) {
        productCommandService.updateProductStatus(currentUser, productId, request.status());
        SellerProductResponse response = productQueryService.getSellerProduct(currentUser, productId);
        return ApiResponse.success(ProductSuccessCode.PRODUCT_STATUS_UPDATED, response);
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(
            @CurrentUser CurrentUserPrincipal currentUser,
            @PathVariable Long productId
    ) {
        productCommandService.deleteProduct(currentUser, productId);
        return ApiResponse.success(ProductSuccessCode.PRODUCT_DELETED, null);
    }
}

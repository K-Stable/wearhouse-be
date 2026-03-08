package com.wearhouse.product.domain.controller;

import com.wearhouse.common.security.current.CurrentUser;
import com.wearhouse.common.security.current.CurrentUserPrincipal;
import com.wearhouse.product.domain.dto.request.ProductCreateRequest;
import com.wearhouse.product.domain.dto.request.ProductStatusUpdateRequest;
import com.wearhouse.product.domain.dto.response.SellerProductListResponse;
import com.wearhouse.product.domain.dto.response.SellerProductResponse;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.service.ProductCommandService;
import com.wearhouse.product.domain.service.ProductQueryService;
import jakarta.validation.Valid;
import java.util.List;
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
public class ProductSellerController {

    private final ProductCommandService productCommandService;
    private final ProductQueryService productQueryService;

    public ProductSellerController(ProductCommandService productCommandService, ProductQueryService productQueryService) {
        this.productCommandService = productCommandService;
        this.productQueryService = productQueryService;
    }

    @PostMapping
    public SellerProductResponse createProduct(
            @CurrentUser CurrentUserPrincipal currentUser,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        Long productId = productCommandService.createProduct(currentUser, request);
        return productQueryService.getSellerProduct(currentUser, productId);
    }

    @GetMapping
    public List<SellerProductListResponse> getSellerProducts(
            @CurrentUser CurrentUserPrincipal currentUser,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return productQueryService.getSellerProducts(currentUser, status, keyword, limit);
    }

    @GetMapping("/{productId}")
    public SellerProductResponse getSellerProduct(
            @CurrentUser CurrentUserPrincipal currentUser,
            @PathVariable Long productId
    ) {
        return productQueryService.getSellerProduct(currentUser, productId);
    }

    @PatchMapping("/{productId}/status")
    public SellerProductResponse updateStatus(
            @CurrentUser CurrentUserPrincipal currentUser,
            @PathVariable Long productId,
            @Valid @RequestBody ProductStatusUpdateRequest request
    ) {
        productCommandService.updateProductStatus(currentUser, productId, request.status());
        return productQueryService.getSellerProduct(currentUser, productId);
    }

    @DeleteMapping("/{productId}")
    public void deleteProduct(
            @CurrentUser CurrentUserPrincipal currentUser,
            @PathVariable Long productId
    ) {
        productCommandService.deleteProduct(currentUser, productId);
    }
}

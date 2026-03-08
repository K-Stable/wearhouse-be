package com.wearhouse.product.domain.controller;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.product.domain.dto.response.BuyerProductDetailResponse;
import com.wearhouse.product.domain.dto.response.BuyerProductListResponse;
import com.wearhouse.product.domain.response.ProductSuccessCode;
import com.wearhouse.product.domain.service.ProductQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/buyer/products")
public class ProductBuyerController {

    private final ProductQueryService productQueryService;

    public ProductBuyerController(ProductQueryService productQueryService) {
        this.productQueryService = productQueryService;
    }

    @GetMapping
    public ApiResponse<List<BuyerProductListResponse>> getBuyerProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "20") int limit
    ) {
        List<BuyerProductListResponse> response = productQueryService.getBuyerProducts(category, keyword, sort, limit);
        return ApiResponse.success(ProductSuccessCode.BUYER_PRODUCT_LIST_FETCHED, response);
    }

    @GetMapping("/{productId}")
    public ApiResponse<BuyerProductDetailResponse> getBuyerProductDetail(@PathVariable Long productId) {
        BuyerProductDetailResponse response = productQueryService.getBuyerProductDetail(productId);
        return ApiResponse.success(ProductSuccessCode.BUYER_PRODUCT_FETCHED, response);
    }
}

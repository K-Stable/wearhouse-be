package com.wearhouse.product.domain.controller;

import com.wearhouse.product.domain.dto.response.BuyerProductDetailResponse;
import com.wearhouse.product.domain.dto.response.BuyerProductListResponse;
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
    public List<BuyerProductListResponse> getBuyerProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return productQueryService.getBuyerProducts(category, keyword, sort, limit);
    }

    @GetMapping("/{productId}")
    public BuyerProductDetailResponse getBuyerProductDetail(@PathVariable Long productId) {
        return productQueryService.getBuyerProductDetail(productId);
    }
}

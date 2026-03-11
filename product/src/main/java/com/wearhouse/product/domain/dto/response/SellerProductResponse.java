package com.wearhouse.product.domain.dto.response;

import com.wearhouse.product.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.util.List;

public record SellerProductResponse(
        Long productId,
        Long sellerId,
        String name,
        BigDecimal price,
        String category,
        String description,
        ProductStatus status,
        String mainImageUrl,
        List<String> previewImageUrls,
        List<String> detailImageUrls,
        List<ProductOptionResponse> options
) {
}

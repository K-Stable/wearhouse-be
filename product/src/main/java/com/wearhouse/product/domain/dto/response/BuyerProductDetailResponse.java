package com.wearhouse.product.domain.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record BuyerProductDetailResponse(
        Long productId,
        String name,
        BigDecimal price,
        String category,
        String description,
        String mainImageUrl,
        List<String> previewImageUrls,
        List<String> detailImageUrls,
        List<ProductOptionResponse> options,
        List<BuyerProductListResponse> similarItems
) {
}

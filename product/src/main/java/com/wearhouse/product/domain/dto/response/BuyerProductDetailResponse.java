package com.wearhouse.product.domain.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BuyerProductDetailResponse(
        Long productId,
        String name,
        BigDecimal price,
        String category,
        String description,
        String sizeGuide,
        String shipping,
        String mainImageUrl,
        List<String> previewImageUrls,
        List<String> detailImageUrls,
        List<ProductOptionResponse> options,
        List<BuyerProductListResponse> similarItems
) {
}

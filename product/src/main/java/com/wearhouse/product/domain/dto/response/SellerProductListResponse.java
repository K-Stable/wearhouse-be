package com.wearhouse.product.domain.dto.response;

import com.wearhouse.product.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.util.List;

public record SellerProductListResponse(
        Long productId,
        String name,
        BigDecimal price,
        String category,
        ProductStatus status,
        String mainImageUrl,
        List<String> sizes,
        List<String> colors,
        Integer totalStock
) {
}

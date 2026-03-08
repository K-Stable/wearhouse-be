package com.wearhouse.product.domain.dto.response;

import java.math.BigDecimal;

public record BuyerProductListResponse(
        Long productId,
        String name,
        BigDecimal price,
        String category,
        String mainImageUrl,
        boolean liked
) {
}

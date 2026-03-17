package com.wearhouse.cart.domain.dto.response;

import java.math.BigDecimal;

public record CartItemResponse(
        Long cartItemId,
        Long productId,
        Long optionId,
        String productName,
        String mainImageUrl,
        String size,
        String color,
        BigDecimal price,
        Integer quantity,
        BigDecimal subtotalPrice
) {
}

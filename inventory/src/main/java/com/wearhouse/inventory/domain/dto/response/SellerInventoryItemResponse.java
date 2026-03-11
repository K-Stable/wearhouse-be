package com.wearhouse.inventory.domain.dto.response;

import java.math.BigDecimal;

public record SellerInventoryItemResponse(
        Long productId,
        String mainImg,
        String name,
        BigDecimal price,
        String category,
        String size,
        String color,
        Integer stockQuantity,
        Integer status
) {
}

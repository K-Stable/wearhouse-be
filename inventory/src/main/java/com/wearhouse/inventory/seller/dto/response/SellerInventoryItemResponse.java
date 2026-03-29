package com.wearhouse.inventory.seller.dto.response;

import java.math.BigDecimal;

public record SellerInventoryItemResponse(
        Long skuId,
        Long productId,
        String mainImg,
        String name,
        BigDecimal price,
        String category,
        String size,
        String color,
        Integer stockQuantity,
        String status
) {
}

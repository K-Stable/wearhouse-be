package com.wearhouse.common.infra.feign.inventory.dto;

import java.math.BigDecimal;

public record InventoryStockUpsertRequest(
        Long skuId,
        Integer availableQty,
        Long sellerId,
        Long productId,
        String productName,
        BigDecimal productPrice,
        String category,
        String productStatus,
        String size,
        String color,
        String mainImageUrl,
        Integer status
) {
}

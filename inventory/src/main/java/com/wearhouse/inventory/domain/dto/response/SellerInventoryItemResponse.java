package com.wearhouse.inventory.domain.dto.response;

import com.wearhouse.inventory.domain.entity.InventoryStockEntity;
import java.math.BigDecimal;

public record SellerInventoryItemResponse(
        Long skuId,
        Long productId,
        String productName,
        String mainImageUrl,
        BigDecimal productPrice,
        String category,
        String size,
        String color,
        Integer availableQty
) {
    public static SellerInventoryItemResponse from(InventoryStockEntity entity) {
        return new SellerInventoryItemResponse(
                entity.getSkuId(),
                entity.getProductId(),
                entity.getProductName(),
                entity.getMainImageUrl(),
                entity.getProductPrice(),
                entity.getProductCategory(),
                entity.getOptionSize(),
                entity.getOptionColor(),
                entity.getAvailableQty()
        );
    }
}

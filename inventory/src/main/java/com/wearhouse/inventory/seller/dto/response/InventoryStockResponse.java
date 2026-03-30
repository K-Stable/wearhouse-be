package com.wearhouse.inventory.seller.dto.response;

import com.wearhouse.inventory.domain.entity.InventoryStockEntity;

import java.math.BigDecimal;

public record InventoryStockResponse(
        Long skuId,
        Long sellerId,
        Long productId,
        String productName,
        BigDecimal productPrice,
        String category,
        String productStatus,
        String size,
        String color,
        String mainImageUrl,
        Integer availableQty,
        Integer reservedQty,
        Long version
) {
    public static InventoryStockResponse from(InventoryStockEntity entity) {
        return new InventoryStockResponse(
                entity.getSkuId(),
                entity.getSellerId(),
                entity.getProductId(),
                entity.getProductName(),
                entity.getProductPrice(),
                entity.getProductCategory(),
                entity.getProductStatus(),
                entity.getOptionSize(),
                entity.getOptionColor(),
                entity.getMainImageUrl(),
                entity.getAvailableQty(),
                entity.getReservedQty(),
                entity.getVersion()
        );
    }
}

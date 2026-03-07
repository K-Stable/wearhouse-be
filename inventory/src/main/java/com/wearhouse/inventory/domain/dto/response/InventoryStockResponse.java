package com.wearhouse.inventory.domain.dto.response;

import com.wearhouse.inventory.domain.entity.InventoryStockEntity;

public record InventoryStockResponse(
        Long skuId,
        Integer availableQty,
        Integer reservedQty,
        Long version
) {
    public static InventoryStockResponse from(InventoryStockEntity entity) {
        return new InventoryStockResponse(
                entity.getSkuId(),
                entity.getAvailableQty(),
                entity.getReservedQty(),
                entity.getVersion()
        );
    }
}

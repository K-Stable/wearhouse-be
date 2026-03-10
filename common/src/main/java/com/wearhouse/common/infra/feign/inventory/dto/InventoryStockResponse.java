package com.wearhouse.common.infra.feign.inventory.dto;

public record InventoryStockResponse(
        Long skuId,
        Integer availableQty,
        Integer reservedQty,
        Long version
) {
}

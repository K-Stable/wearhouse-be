package com.wearhouse.product.infra.feign.dto;

public record InventoryStockResponse(
        Long skuId,
        Integer availableQty,
        Integer reservedQty,
        Long version
) {
}

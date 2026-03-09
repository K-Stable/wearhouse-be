package com.wearhouse.product.infra.feign.dto;

public record InventoryStockUpsertRequest(
        Long skuId,
        Integer availableQty
) {
}

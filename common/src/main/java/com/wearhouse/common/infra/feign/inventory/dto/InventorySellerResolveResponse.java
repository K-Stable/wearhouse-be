package com.wearhouse.common.infra.feign.inventory.dto;

import java.util.List;

public record InventorySellerResolveResponse(
        List<InventorySkuSellerLine> items
) {
    public record InventorySkuSellerLine(
            Long skuId,
            Long sellerId
    ) {
    }
}

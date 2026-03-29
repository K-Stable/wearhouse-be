package com.wearhouse.inventory.internal.dto.response;

import java.util.List;

public record InventorySellerResolveResponse(
        List<InventorySkuSellerLineResponse> items
) {
    public record InventorySkuSellerLineResponse(
            Long skuId,
            Long sellerId
    ) {
    }
}

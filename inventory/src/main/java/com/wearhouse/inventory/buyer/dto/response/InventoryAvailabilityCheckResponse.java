package com.wearhouse.inventory.buyer.dto.response;

import java.util.List;

public record InventoryAvailabilityCheckResponse(
        boolean available,
        List<InventoryAvailabilityLineResponse> items
) {

    public record InventoryAvailabilityLineResponse(
            Long productId,
            Long optionId,
            Long skuId,
            Integer requestedQty,
            Integer availableQty,
            boolean available
    ) {
    }
}

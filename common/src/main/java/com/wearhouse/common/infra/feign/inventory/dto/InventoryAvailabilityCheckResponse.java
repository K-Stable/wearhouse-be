package com.wearhouse.common.infra.feign.inventory.dto;

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


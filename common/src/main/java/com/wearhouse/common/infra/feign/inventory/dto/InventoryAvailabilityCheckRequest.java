package com.wearhouse.common.infra.feign.inventory.dto;

import java.util.List;

public record InventoryAvailabilityCheckRequest(
        List<InventoryAvailabilityLineRequest> items
) {
    public record InventoryAvailabilityLineRequest(
            Long productId,
            Long optionId,
            Integer quantity
    ) {
    }
}


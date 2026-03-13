package com.wearhouse.inventory.domain.dto.request;

import jakarta.validation.constraints.Min;

public record InventoryStockUpdateRequest(
        @Min(0) Integer availableQty,
        String productStatus
) {
}

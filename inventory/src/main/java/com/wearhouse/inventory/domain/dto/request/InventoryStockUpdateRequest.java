package com.wearhouse.inventory.domain.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record InventoryStockUpdateRequest(
        @Min(0) Integer availableQty,
        @Min(0) @Max(1) Integer status
) {
}

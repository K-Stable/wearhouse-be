package com.wearhouse.inventory.domain.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InventoryStockUpsertRequest(
        @NotNull Long skuId,
        @NotNull @Min(0) Integer availableQty
) {
}

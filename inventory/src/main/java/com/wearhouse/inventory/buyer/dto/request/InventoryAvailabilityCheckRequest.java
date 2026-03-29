package com.wearhouse.inventory.buyer.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record InventoryAvailabilityCheckRequest(
        @NotEmpty @Valid List<InventoryAvailabilityLineRequest> items
) {

    public record InventoryAvailabilityLineRequest(
            @NotNull Long productId,
            Long optionId,
            @NotNull @Min(1) Integer quantity
    ) {
    }
}

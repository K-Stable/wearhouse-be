package com.wearhouse.inventory.buyer.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record InventoryOrderPreviewRequest(
        @NotEmpty @Valid List<InventoryOrderPreviewItemRequest> items
) {
    public record InventoryOrderPreviewItemRequest(
            @NotNull Long productId,
            @NotBlank String color,
            @NotBlank String size,
            @NotNull @Min(1) Integer quantity
    ) {
    }
}

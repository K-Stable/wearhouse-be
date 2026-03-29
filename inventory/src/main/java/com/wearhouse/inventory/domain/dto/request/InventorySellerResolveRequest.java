package com.wearhouse.inventory.domain.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record InventorySellerResolveRequest(
        @NotEmpty List<@NotNull Long> skuIds
) {
}

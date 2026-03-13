package com.wearhouse.common.infra.feign.inventory.dto;

import java.util.List;

public record InventoryOrderPreviewRequest(
        List<InventoryOrderPreviewItemRequest> items
) {
    public record InventoryOrderPreviewItemRequest(
            Long productId,
            String color,
            String size,
            Integer quantity
    ) {
    }
}

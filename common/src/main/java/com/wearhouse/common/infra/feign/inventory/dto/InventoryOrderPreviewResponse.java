package com.wearhouse.common.infra.feign.inventory.dto;

import java.math.BigDecimal;
import java.util.List;

public record InventoryOrderPreviewResponse(
        List<InventoryOrderPreviewLine> items
) {
    public record InventoryOrderPreviewLine(
            Long productId,
            Long optionId,
            Long sellerId,
            String productName,
            BigDecimal unitPrice,
            String color,
            String size,
            String mainImageUrl,
            String productStatus,
            Integer requestedQuantity,
            Integer availableQuantity,
            boolean available
    ) {
    }
}

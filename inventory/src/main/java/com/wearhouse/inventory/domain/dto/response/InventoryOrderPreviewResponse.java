package com.wearhouse.inventory.domain.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record InventoryOrderPreviewResponse(
        List<InventoryOrderPreviewLineResponse> items
) {
    public record InventoryOrderPreviewLineResponse(
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

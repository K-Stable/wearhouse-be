package com.wearhouse.inventory.kafka.dto;

import java.util.List;

public record InventoryReserveRequestedEvent(
        Long orderId,
        String orderNo,
        List<Item> items
) {
    public record Item(
            Long productId,
            Long optionId,
            Integer quantity
    ) {
    }
}


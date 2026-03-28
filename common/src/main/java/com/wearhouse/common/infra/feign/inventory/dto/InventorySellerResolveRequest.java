package com.wearhouse.common.infra.feign.inventory.dto;

import java.util.List;

public record InventorySellerResolveRequest(
        List<Long> skuIds
) {
}

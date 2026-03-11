package com.wearhouse.common.infra.feign.product.dto;

import java.util.List;

public record ProductSoldOutSyncRequest(
        List<Long> productIds
) {
}

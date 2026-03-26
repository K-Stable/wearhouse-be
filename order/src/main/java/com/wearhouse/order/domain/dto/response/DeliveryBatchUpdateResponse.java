package com.wearhouse.order.domain.dto.response;

import java.util.List;

public record DeliveryBatchUpdateResponse(
        int processedCount,
        List<Long> orderIds
) {
}

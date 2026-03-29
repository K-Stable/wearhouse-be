package com.wearhouse.order.delivery.dto.response;

import java.util.List;

public record DeliveryBatchUpdateResponse(
        int processedCount,
        List<Long> orderIds
) {
}

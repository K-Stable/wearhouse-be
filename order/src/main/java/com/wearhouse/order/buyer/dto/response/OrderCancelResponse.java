package com.wearhouse.order.buyer.dto.response;

import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record OrderCancelResponse(
        String orderNo,
        String status,
        String reasonCode,
        LocalDateTime cancelledAt
) {
}

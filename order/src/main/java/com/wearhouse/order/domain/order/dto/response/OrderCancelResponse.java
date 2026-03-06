package com.wearhouse.order.domain.order.dto.response;

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

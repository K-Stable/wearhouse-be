package com.wearhouse.order.domain.order.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record OrderCreateResponse(
        Long orderId,
        String orderNo,
        String status,
        BigDecimal payAmount,
        String sagaId,
        String outboxEventId,
        LocalDateTime orderedAt
) {
}

package com.wearhouse.order.domain.order.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record OrderSummaryResponse(
        String orderNo,
        String status,
        BigDecimal payAmount,
        LocalDateTime orderedAt
) {
}

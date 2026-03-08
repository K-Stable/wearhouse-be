package com.wearhouse.user.domain.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserOrderSummaryResponse(
        String orderNo,
        String status,
        BigDecimal payAmount,
        LocalDateTime orderedAt
) {
}

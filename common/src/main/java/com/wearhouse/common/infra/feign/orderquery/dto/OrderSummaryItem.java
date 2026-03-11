package com.wearhouse.common.infra.feign.orderquery.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderSummaryItem(
        String orderNo,
        String status,
        BigDecimal payAmount,
        LocalDateTime orderedAt
) {
}

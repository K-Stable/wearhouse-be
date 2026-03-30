package com.wearhouse.order.buyer.dto.response;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record OrderCreateResponse(
        String orderNo,
        String customerId,
        String customerName,
        BigDecimal payAmount,
        String checkoutSessionId,
        String checkoutUrl,
        String appLaunchUrl,
        String checkoutExpiresAt
) {
}

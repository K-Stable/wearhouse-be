package com.wearhouse.order.domain.dto.response;

public record OrderPaymentPrepareResponse(
        String checkoutSessionId,
        String checkoutUrl,
        String appLaunchUrl,
        String checkoutExpiresAt
) {
}

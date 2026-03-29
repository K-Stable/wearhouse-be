package com.wearhouse.order.buyer.dto.response;

public record OrderPaymentPrepareResponse(
        String checkoutSessionId,
        String checkoutUrl,
        String appLaunchUrl,
        String checkoutExpiresAt
) {
}

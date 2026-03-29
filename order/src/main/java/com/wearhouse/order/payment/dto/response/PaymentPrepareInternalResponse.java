package com.wearhouse.order.payment.dto.response;

public record PaymentPrepareInternalResponse(
        String checkoutSessionId,
        String checkoutUrl,
        String appLaunchUrl,
        String checkoutExpiresAt
) {
}

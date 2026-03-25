package com.wearhouse.order.infra.payment.dto;

public record PaymentPrepareInternalResponse(
        String checkoutSessionId,
        String checkoutUrl,
        String appLaunchUrl,
        String checkoutExpiresAt
) {
}

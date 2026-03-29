package com.wearhouse.payment.internal.dto.response;

public record WalletPrepareResponse(
        String checkoutSessionId,
        String checkoutUrl,
        String appLaunchUrl,
        String checkoutExpiresAt
) {
}

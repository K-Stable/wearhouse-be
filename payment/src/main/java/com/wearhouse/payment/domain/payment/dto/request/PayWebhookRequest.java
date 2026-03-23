package com.wearhouse.payment.domain.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PayWebhookRequest(
        String eventId,
        String eventType,
        String occurredAt,
        PaymentWebhookPayload payment
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentWebhookPayload(
            String paymentKey,
            String paymentId,
            String status,
            String merchantKey,
            String payerAddress,
            String tokenAddress,
            String amount,
            String txHash,
            String commandId,
            String commandStatus,
            String reasonCode,
            String reasonMessage
    ) {
    }
}

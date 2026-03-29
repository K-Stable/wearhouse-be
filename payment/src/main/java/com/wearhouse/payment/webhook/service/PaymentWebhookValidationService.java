package com.wearhouse.payment.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.payment.webhook.dto.request.PayWebhookRequest;
import com.wearhouse.payment.webhook.security.PaymentWebhookSignatureVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentWebhookValidationService {

    private final ObjectMapper objectMapper;
    private final PaymentWebhookSignatureVerifier signatureVerifier;

    public PayWebhookRequest validateAndRead(
            String timestamp,
            String signature,
            String rawBody
    ) throws Exception {
        signatureVerifier.validate(timestamp, signature, rawBody);
        PayWebhookRequest webhookRequest = objectMapper.readValue(rawBody, PayWebhookRequest.class);
        validateWebhookRequest(webhookRequest);
        return webhookRequest;
    }

    private void validateWebhookRequest(PayWebhookRequest request) {
        if (request == null || request.eventId() == null || request.eventId().isBlank()) {
            throw new IllegalArgumentException("eventId 값이 필요합니다.");
        }
        if (request.eventType() == null || request.eventType().isBlank()) {
            throw new IllegalArgumentException("eventType 값이 필요합니다.");
        }
        if (request.payment() == null) {
            throw new IllegalArgumentException("payment payload 값이 필요합니다.");
        }
    }
}


package com.wearhouse.payment.kafka.handler;

import com.wearhouse.payment.internal.service.PaymentCommandService;
import com.wearhouse.payment.kafka.dto.PaymentPrepareRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentPrepareRequestedHandler {

    private final PaymentCommandService paymentCommandService;

    public void handle(
            String eventId,
            String topic,
            String partitionKey,
            String rawPayload,
            PaymentPrepareRequestedEvent payload
    ) {
        paymentCommandService.handlePaymentPrepareRequested(eventId, topic, partitionKey, rawPayload, payload);
    }
}

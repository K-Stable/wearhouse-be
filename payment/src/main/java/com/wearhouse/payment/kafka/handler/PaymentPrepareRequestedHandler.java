package com.wearhouse.payment.kafka.handler;

import com.wearhouse.payment.internal.service.PaymentInternalCommandService;
import com.wearhouse.payment.kafka.dto.PaymentPrepareRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentPrepareRequestedHandler {

    private final PaymentInternalCommandService paymentInternalCommandService;

    public void handle(
            String eventId,
            String topic,
            String partitionKey,
            String rawPayload,
            PaymentPrepareRequestedEvent payload
    ) {
        paymentInternalCommandService.handlePaymentPrepareRequested(eventId, topic, partitionKey, rawPayload, payload);
    }
}

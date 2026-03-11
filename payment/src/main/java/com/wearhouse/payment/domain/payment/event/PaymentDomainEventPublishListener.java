package com.wearhouse.payment.domain.payment.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.payment.infra.kafka.service.PaymentKafkaPublishService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaymentDomainEventPublishListener {

    private final ObjectMapper objectMapper;
    private final PaymentKafkaPublishService paymentKafkaPublishService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(PaymentDomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event.toEnvelope());
            paymentKafkaPublishService.send(
                    event.getEventId(),
                    event.getEventType(),
                    event.getTopic(),
                    event.getPartitionKey(),
                    payload,
                    0,
                    "after_commit"
            );
        } catch (JsonProcessingException exception) {
            paymentKafkaPublishService.send(
                    event.getEventId(),
                    event.getEventType(),
                    event.getTopic(),
                    event.getPartitionKey(),
                    "{\"serializationError\":true}",
                    0,
                    "serialization_fallback"
            );
        }
    }
}

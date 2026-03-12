package com.wearhouse.payment.domain.payment.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.support.event.ExternalEventMessageListener;
import com.wearhouse.payment.infra.kafka.producer.PaymentKafkaProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaymentOutboxPublishListener implements ExternalEventMessageListener<PaymentDomainEvent> {

    private final ObjectMapper objectMapper;
    private final PaymentKafkaProducer paymentKafkaProducer;

    @Override
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendMessageHandler(PaymentDomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event.toEnvelope());
            paymentKafkaProducer.send(
                    event.getEventId(),
                    event.getEventType(),
                    event.getTopic(),
                    event.getPartitionKey(),
                    payload,
                    0,
                    "after_commit"
            );
        } catch (JsonProcessingException exception) {
            paymentKafkaProducer.send(
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

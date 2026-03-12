package com.wearhouse.order.domain.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.support.event.ExternalEventMessageListener;
import com.wearhouse.order.infra.kafka.producer.OrderKafkaProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderOutboxPublishListener implements ExternalEventMessageListener<OrderDomainEvent> {

    private final ObjectMapper objectMapper;
    private final OrderKafkaProducer orderKafkaProducer;


    @Override
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendMessageHandler(OrderDomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event.toEnvelope());
            orderKafkaProducer.send(
                    event.getEventId(),
                    event.getEventType(),
                    event.getTopic(),
                    event.getPartitionKey(),
                    payload,
                    "after_commit"
            );
        } catch (JsonProcessingException exception) {
            orderKafkaProducer.send(
                    event.getEventId(),
                    event.getEventType(),
                    event.getTopic(),
                    event.getPartitionKey(),
                    "{\"serializationError\":true}",
                    "serialization_fallback"
            );
        }
    }
}

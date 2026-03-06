package com.wearhouse.order.domain.order.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.order.infra.kafka.service.OrderOutboxKafkaPublishService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderOutboxPublishListener {

    private final ObjectMapper objectMapper;
    private final OrderOutboxKafkaPublishService orderOutboxKafkaPublishService;

    public OrderOutboxPublishListener(
            ObjectMapper objectMapper,
            OrderOutboxKafkaPublishService orderOutboxKafkaPublishService
    ) {
        this.objectMapper = objectMapper;
        this.orderOutboxKafkaPublishService = orderOutboxKafkaPublishService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(OrderDomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event.toEnvelope());
            orderOutboxKafkaPublishService.send(
                    event.getEventId(),
                    event.getEventType(),
                    event.getTopic(),
                    event.getPartitionKey(),
                    payload,
                    0,
                    "after_commit"
            );
        } catch (JsonProcessingException exception) {
            orderOutboxKafkaPublishService.send(
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

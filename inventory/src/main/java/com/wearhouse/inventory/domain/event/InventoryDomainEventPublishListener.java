package com.wearhouse.inventory.domain.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.inventory.infra.kafka.service.InventoryKafkaPublishService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class InventoryDomainEventPublishListener {

    private final ObjectMapper objectMapper;
    private final InventoryKafkaPublishService inventoryKafkaPublishService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(InventoryDomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event.toEnvelope());
            inventoryKafkaPublishService.send(
                    event.getEventId(),
                    event.getEventType(),
                    event.getTopic(),
                    event.getPartitionKey(),
                    payload,
                    0,
                    "after_commit"
            );
        } catch (JsonProcessingException exception) {
            inventoryKafkaPublishService.send(
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

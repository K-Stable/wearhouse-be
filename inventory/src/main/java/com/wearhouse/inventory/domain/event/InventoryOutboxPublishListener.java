package com.wearhouse.inventory.domain.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.support.event.ExternalEventMessageListener;
import com.wearhouse.inventory.infra.kafka.producer.InventoryKafkaProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class InventoryOutboxPublishListener implements ExternalEventMessageListener<InventoryDomainEvent> {

    private final ObjectMapper objectMapper;
    private final InventoryKafkaProducer inventoryKafkaProducer;

    @Override
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendMessageHandler(InventoryDomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event.toEnvelope());
            inventoryKafkaProducer.send(
                    event.getEventId(),
                    event.getEventType(),
                    event.getTopic(),
                    event.getPartitionKey(),
                    payload,
                    0,
                    "after_commit"
            );
        } catch (JsonProcessingException exception) {
            inventoryKafkaProducer.send(
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

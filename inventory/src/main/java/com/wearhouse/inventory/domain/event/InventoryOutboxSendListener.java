package com.wearhouse.inventory.domain.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.inventory.domain.repository.InventoryOutboxRepository;
import com.wearhouse.inventory.infra.kafka.producer.InventoryKafkaProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class InventoryOutboxSendListener {

    private final ObjectMapper objectMapper;
    private final InventoryOutboxRepository inventoryOutboxRepository;
    private final InventoryKafkaProducer inventoryKafkaProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendMessageHandler(InventoryDomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event.toEnvelope());
            inventoryKafkaProducer.send(
                    event.getTopic(),
                    event.getPartitionKey(),
                    payload
            );
            inventoryOutboxRepository.markSuccess(event.getEventId());
        } catch (JsonProcessingException exception) {
            inventoryOutboxRepository.markFail(event.getEventId(), "SERIALIZE_ERROR", exception.getMessage());
            throw new IllegalStateException("Inventory Outbox 이벤트 직렬화에 실패했습니다.", exception);
        } catch (Exception exception) {
            inventoryOutboxRepository.markFail(event.getEventId(), "KAFKA_SEND_ERROR", exception.getMessage());
            throw new IllegalStateException("Inventory Outbox 이벤트 전송에 실패했습니다.", exception);
        }
    }
}

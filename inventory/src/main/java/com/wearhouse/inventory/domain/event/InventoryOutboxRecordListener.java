package com.wearhouse.inventory.domain.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.inventory.domain.repository.InventoryOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class InventoryOutboxRecordListener {

    private final InventoryOutboxRepository inventoryOutboxRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void recordMessageHandler(InventoryDomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event.toEnvelope());
            inventoryOutboxRepository.saveReady(
                    event.getEventId(),
                    event.getEventType(),
                    event.getTopic(),
                    event.getPartitionKey(),
                    payload
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Inventory Outbox 이벤트 직렬화에 실패했습니다.", exception);
        }
    }
}

package com.wearhouse.inventory.domain.repository;

import com.wearhouse.inventory.domain.entity.InventoryInboxEventEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryInboxRepository {

    private final InventoryInboxEventRepository inventoryInboxEventRepository;

    public InventoryInboxRepository(InventoryInboxEventRepository inventoryInboxEventRepository) {
        this.inventoryInboxEventRepository = inventoryInboxEventRepository;
    }

    public boolean tryReceive(
            String eventId,
            String consumerName,
            String eventType,
            String topic,
            String partitionKey,
            String payload
    ) {
        InventoryInboxEventEntity entity = InventoryInboxEventEntity.received(
                eventId,
                consumerName,
                eventType,
                topic,
                partitionKey,
                payload
        );
        try {
            inventoryInboxEventRepository.saveAndFlush(entity);
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    public void markProcessed(String eventId, String consumerName) {
        inventoryInboxEventRepository.findByEventIdAndConsumerName(eventId, consumerName)
                .ifPresent(InventoryInboxEventEntity::markProcessed);
    }

    public void markFailed(String eventId, String consumerName, String reasonCode, String reasonMessage) {
        inventoryInboxEventRepository.findByEventIdAndConsumerName(eventId, consumerName)
                .ifPresent(entity -> entity.markFailed(reasonCode, reasonMessage));
    }
}

package com.wearhouse.inventory.domain.repository;

import com.wearhouse.inventory.domain.entity.InventoryInboxEventEntity;
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
        return inventoryInboxEventRepository.insertIgnoreReceived(eventId, consumerName) > 0;
    }

    public void markProcessed(String eventId, String consumerName) {
        inventoryInboxEventRepository.findByEventIdAndConsumerName(eventId, consumerName)
                .ifPresent(InventoryInboxEventEntity::markProcessed);
    }

    public void markFailed(String eventId, String consumerName, String reasonCode, String reasonMessage) {
        inventoryInboxEventRepository.findByEventIdAndConsumerName(eventId, consumerName)
                .ifPresent(InventoryInboxEventEntity::markFailed);
    }
}

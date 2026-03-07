package com.wearhouse.inventory.domain.event;

import com.wearhouse.inventory.infra.kafka.service.InventoryKafkaPublishService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InventoryDomainEventPublishListener {

    private final InventoryKafkaPublishService inventoryKafkaPublishService;

    public InventoryDomainEventPublishListener(InventoryKafkaPublishService inventoryKafkaPublishService) {
        this.inventoryKafkaPublishService = inventoryKafkaPublishService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(InventoryDomainEvent event) {
        inventoryKafkaPublishService.send(event);
    }
}

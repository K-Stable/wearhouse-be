package com.wearhouse.inventory.scheduler;

import com.wearhouse.common.global.transactional.WriteTx;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wearhouse.outbox.relay.enabled", havingValue = "true")
@RequiredArgsConstructor
public class InventoryOutboxRelayScheduler {

    private final InventoryOutboxRepublishBatchService inventoryOutboxRepublishBatchService;

    @WriteTx
    @Scheduled(fixedDelayString = "${wearhouse.outbox.republish-interval-ms:500}")
    public void publishOutboxEvents() {
        inventoryOutboxRepublishBatchService.publishOutboxEvents();
    }
}

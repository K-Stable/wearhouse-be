package com.wearhouse.inventory.domain.service.command;

import com.wearhouse.common.global.transactional.WriteTx;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryOutboxRelayScheduler {

    private final InventoryOutboxRepublishBatchService inventoryOutboxRepublishBatchService;

    @WriteTx
    @Scheduled(fixedDelayString = "${wearhouse.outbox.republish-interval-ms:500}")
    public void publishOutboxEvents() {
        inventoryOutboxRepublishBatchService.publishOutboxEvents();
    }
}

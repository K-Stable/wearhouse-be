package com.wearhouse.inventory.domain.service.command;

import com.wearhouse.inventory.support.monitoring.InventoryKafkaFlowMetrics;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InventoryReservationScheduler {

    private final InventoryCommandService inventoryCommandService;
    private final InventoryKafkaFlowMetrics inventoryKafkaFlowMetrics;

    public InventoryReservationScheduler(
            InventoryCommandService inventoryCommandService,
            InventoryKafkaFlowMetrics inventoryKafkaFlowMetrics
    ) {
        this.inventoryCommandService = inventoryCommandService;
        this.inventoryKafkaFlowMetrics = inventoryKafkaFlowMetrics;
    }

    @Transactional
    @Scheduled(fixedDelayString = "${wearhouse.inventory.reservation-expire-interval-ms:30000}")
    public void releaseExpiredReservations() {
        int releasedCount = inventoryCommandService.releaseExpiredReservations();
        inventoryKafkaFlowMetrics.incrementExpiredReleaseCount(releasedCount);
    }
}

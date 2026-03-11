package com.wearhouse.inventory.domain.service.command;

import com.wearhouse.inventory.support.monitoring.InventoryKafkaFlowMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.wearhouse.common.global.transactional.WriteTx;

@Component
@RequiredArgsConstructor
public class InventoryReservationScheduler {

    private final InventoryCommandService inventoryCommandService;
    private final InventoryKafkaFlowMetrics inventoryKafkaFlowMetrics;

    @WriteTx
    @Scheduled(fixedDelayString = "${wearhouse.inventory.reservation-expire-interval-ms:30000}")
    public void releaseExpiredReservations() {
        int releasedCount = inventoryCommandService.releaseExpiredReservations();
        inventoryKafkaFlowMetrics.incrementExpiredReleaseCount(releasedCount);
    }
}

package com.wearhouse.inventory.domain.service.command;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InventoryReservationScheduler {

    private final InventoryCommandService inventoryCommandService;

    public InventoryReservationScheduler(InventoryCommandService inventoryCommandService) {
        this.inventoryCommandService = inventoryCommandService;
    }

    @Transactional
    @Scheduled(fixedDelayString = "${wearhouse.inventory.reservation-expire-interval-ms:30000}")
    public void releaseExpiredReservations() {
        inventoryCommandService.releaseExpiredReservations();
    }
}

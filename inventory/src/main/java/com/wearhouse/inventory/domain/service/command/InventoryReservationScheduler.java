package com.wearhouse.inventory.domain.service.command;

import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.inventory.domain.service.buyer.command.BuyerInventoryCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryReservationScheduler {

    private final BuyerInventoryCommandService buyerInventoryCommandService;

    @WriteTx
    @Scheduled(fixedDelayString = "${wearhouse.inventory.reservation-expire-interval-ms:30000}")
    public void releaseExpiredReservations() {
        buyerInventoryCommandService.releaseExpiredReservations();
    }
}

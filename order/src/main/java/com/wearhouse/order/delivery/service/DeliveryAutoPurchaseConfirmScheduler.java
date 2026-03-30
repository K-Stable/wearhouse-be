package com.wearhouse.order.delivery.service;

import com.wearhouse.common.global.transactional.WriteTx;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeliveryAutoPurchaseConfirmScheduler {

    private final DeliveryCommandService deliveryCommandService;

    @WriteTx
    @Scheduled(fixedDelayString = "${wearhouse.order.delivery-purchase-confirm-scheduler-interval-ms:60000}")
    public void autoConfirmDeliveredOrders() {
        deliveryCommandService.autoConfirmDeliveredOrders();
    }
}

package com.wearhouse.order.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryValidationService {

    private final DeliveryCommandService deliveryCommandService;

    public boolean isCancelBlockedByDelivery(Long orderId) {
        return deliveryCommandService.isCancelBlockedByDelivery(orderId);
    }
}


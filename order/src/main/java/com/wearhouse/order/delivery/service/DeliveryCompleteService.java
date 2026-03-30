package com.wearhouse.order.delivery.service;

import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.order.delivery.dto.request.DeliveryDeliveredRequest;
import com.wearhouse.order.delivery.dto.response.DeliveryBatchUpdateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryCompleteService {

    private final DeliveryCommandService deliveryCommandService;

    public DeliveryBatchUpdateResponse markDelivered(
            LoginUser currentUser,
            DeliveryDeliveredRequest request
    ) {
        return deliveryCommandService.markDelivered(currentUser, request);
    }
}


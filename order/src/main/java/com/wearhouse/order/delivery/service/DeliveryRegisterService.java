package com.wearhouse.order.delivery.service;

import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.order.delivery.dto.request.DeliveryRegisterRequest;
import com.wearhouse.order.delivery.dto.response.DeliveryBatchUpdateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryRegisterService {

    private final DeliveryCommandService deliveryCommandService;

    public DeliveryBatchUpdateResponse registerDeliveries(
            LoginUser currentUser,
            DeliveryRegisterRequest request
    ) {
        return deliveryCommandService.registerDeliveries(currentUser, request);
    }
}


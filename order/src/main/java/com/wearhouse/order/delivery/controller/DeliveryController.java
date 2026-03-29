package com.wearhouse.order.delivery.controller;

import com.wearhouse.common.security.current.LoginSeller;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.order.delivery.dto.request.DeliveryDeliveredRequest;
import com.wearhouse.order.delivery.dto.request.DeliveryRegisterRequest;
import com.wearhouse.order.delivery.dto.response.DeliveryBatchUpdateResponse;
import com.wearhouse.order.delivery.service.DeliveryCompleteService;
import com.wearhouse.order.delivery.service.DeliveryRegisterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryRegisterService deliveryRegisterService;
    private final DeliveryCompleteService deliveryCompleteService;

    @PostMapping("/seller/orders/deliveries")
    public DeliveryBatchUpdateResponse registerDeliveries(
            @LoginSeller LoginUser currentUser,
            @Valid @RequestBody DeliveryRegisterRequest request
    ) {
        return deliveryRegisterService.registerDeliveries(currentUser, request);
    }

    @PatchMapping("/seller/orders/deliveries/delivered")
    public DeliveryBatchUpdateResponse markDeliveriesDelivered(
            @LoginSeller LoginUser currentUser,
            @Valid @RequestBody DeliveryDeliveredRequest request
    ) {
        return deliveryCompleteService.markDelivered(currentUser, request);
    }
}

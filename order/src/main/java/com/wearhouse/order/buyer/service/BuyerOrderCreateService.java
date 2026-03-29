package com.wearhouse.order.buyer.service;

import com.wearhouse.order.buyer.dto.request.OrderCreateRequest;
import com.wearhouse.order.buyer.dto.response.OrderCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuyerOrderCreateService {

    private final OrderCreateOrchestrationService orderCreateOrchestrationService;

    public OrderCreateResponse createOrder(OrderCreateRequest request) {
        return orderCreateOrchestrationService.createOrder(request);
    }
}

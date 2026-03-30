package com.wearhouse.order.buyer.service;

import com.wearhouse.order.buyer.dto.request.OrderCreateRequest;
import com.wearhouse.order.buyer.dto.response.OrderCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuyerOrderCreateService {

    private final BuyerOrderCreateOrchestrationService orderCreateOrchestrationService;

    public OrderCreateResponse createMemberOrder(Long buyerId, OrderCreateRequest request) {
        return orderCreateOrchestrationService.createOrder(buyerId, request);
    }

    public OrderCreateResponse createGuestOrder(OrderCreateRequest request) {
        return orderCreateOrchestrationService.createOrder(null, request);
    }
}

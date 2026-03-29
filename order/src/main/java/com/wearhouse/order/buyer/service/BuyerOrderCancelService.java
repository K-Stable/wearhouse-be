package com.wearhouse.order.buyer.service;

import com.wearhouse.order.buyer.dto.request.OrderCancelRequest;
import com.wearhouse.order.buyer.dto.response.OrderCancelResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuyerOrderCancelService {

    private final OrderCancelOrchestrationService orderCancelOrchestrationService;

    public OrderCancelResponse cancelOrder(String orderNo, OrderCancelRequest request) {
        return orderCancelOrchestrationService.cancelOrder(orderNo, request);
    }
}

package com.wearhouse.order.buyer.service;

import com.wearhouse.order.buyer.dto.request.OrderPaymentConfirmRequest;
import com.wearhouse.order.buyer.dto.response.OrderPaymentConfirmResponse;
import com.wearhouse.order.payment.service.OrderPaymentOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuyerOrderPaymentConfirmService {

    private final OrderPaymentOrchestrationService orderPaymentOrchestrationService;

    public OrderPaymentConfirmResponse confirmPayment(Long buyerId, String orderNo, OrderPaymentConfirmRequest request) {
        return orderPaymentOrchestrationService.confirmPayment(buyerId, orderNo, request);
    }
}

package com.wearhouse.order.buyer.service;

import com.wearhouse.order.buyer.dto.response.OrderPaymentPrepareResponse;
import com.wearhouse.order.payment.service.OrderPaymentOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuyerOrderPaymentPrepareService {

    private final OrderPaymentOrchestrationService orderPaymentOrchestrationService;

    public OrderPaymentPrepareResponse preparePayment(Long buyerId, String orderNo, String idempotencyKey) {
        return orderPaymentOrchestrationService.preparePayment(buyerId, orderNo, idempotencyKey);
    }
}

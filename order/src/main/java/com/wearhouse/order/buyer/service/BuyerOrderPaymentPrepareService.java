package com.wearhouse.order.buyer.service;

import com.wearhouse.order.buyer.dto.response.OrderPaymentPrepareResponse;
import com.wearhouse.order.paymentintegration.service.OrderPaymentIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuyerOrderPaymentPrepareService {

    private final OrderPaymentIntegrationService orderPaymentIntegrationService;

    public OrderPaymentPrepareResponse preparePayment(Long buyerId, String orderNo, String idempotencyKey) {
        return orderPaymentIntegrationService.preparePayment(buyerId, orderNo, idempotencyKey);
    }
}

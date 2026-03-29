package com.wearhouse.order.buyer.service;

import com.wearhouse.order.buyer.dto.request.OrderPaymentConfirmRequest;
import com.wearhouse.order.buyer.dto.response.OrderPaymentConfirmResponse;
import com.wearhouse.order.paymentintegration.service.OrderPaymentIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuyerOrderPaymentConfirmService {

    private final OrderPaymentIntegrationService orderPaymentIntegrationService;

    public OrderPaymentConfirmResponse confirmPayment(Long buyerId, String orderNo, OrderPaymentConfirmRequest request) {
        return orderPaymentIntegrationService.confirmPayment(buyerId, orderNo, request);
    }
}

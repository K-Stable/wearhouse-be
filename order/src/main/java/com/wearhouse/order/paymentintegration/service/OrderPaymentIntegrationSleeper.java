package com.wearhouse.order.paymentintegration.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.order.common.exception.OrderErrorCode;
import org.springframework.stereotype.Component;

@Component
public class OrderPaymentIntegrationSleeper {

    public void sleep(long intervalMs) {
        try {
            Thread.sleep(Math.max(10, intervalMs));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ErrorException(OrderErrorCode.INVENTORY_PREVIEW_UNAVAILABLE, "주문 상태 대기 중 인터럽트가 발생했습니다.");
        }
    }
}

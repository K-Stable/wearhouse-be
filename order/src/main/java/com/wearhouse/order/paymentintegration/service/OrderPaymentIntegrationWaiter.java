package com.wearhouse.order.paymentintegration.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.order.common.exception.OrderErrorCode;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.support.config.OrderProperties;
import com.wearhouse.order.support.monitoring.OrderFlowMetrics;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderPaymentIntegrationWaiter {

    private final OrderRepository orderRepository;
    private final EntityManager entityManager;
    private final OrderProperties orderProperties;
    private final OrderPaymentIntegrationValidator orderPaymentIntegrationValidator;
    private final OrderPaymentIntegrationSleeper orderPaymentIntegrationSleeper;
    private final OrderFlowMetrics orderFlowMetrics;

    public OrderEntity waitForPrepareTarget(OrderEntity order) {
        OrderEntity initial = order;
        OrderEntity current = order;
        long startedAt = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startedAt) < orderProperties.paymentPrepareWaitTimeoutMs()) {
            if (!orderPaymentIntegrationValidator.shouldWaitForPrepare(current.getStatus())) {
                orderFlowMetrics.recordPrepareWait(
                        initial.getStatus(),
                        current.getStatus(),
                        false,
                        System.currentTimeMillis() - startedAt
                );
                return current;
            }
            orderPaymentIntegrationSleeper.sleep(orderProperties.paymentPrepareWaitIntervalMs());
            entityManager.clear();
            current = findOrder(order.getId());
        }
        entityManager.clear();
        OrderEntity resolved = findOrder(order.getId());
        orderFlowMetrics.recordPrepareWait(
                initial.getStatus(),
                resolved.getStatus(),
                true,
                System.currentTimeMillis() - startedAt
        );
        return resolved;
    }

    private OrderEntity findOrder(Long orderId) {
        return orderRepository.findDetailById(orderId)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
    }
}

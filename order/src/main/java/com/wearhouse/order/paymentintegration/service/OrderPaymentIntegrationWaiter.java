package com.wearhouse.order.paymentintegration.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.order.common.exception.OrderErrorCode;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.support.config.OrderProperties;
import jakarta.persistence.EntityManager;
import java.util.Optional;
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

    public OrderStatus waitForConfirmResult(Long orderId, String paymentStatus) {
        if ("PENDING".equalsIgnoreCase(paymentStatus)) {
            return findOrderStatus(orderId);
        }

        long startedAt = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startedAt) < orderProperties.paymentConfirmWaitTimeoutMs()) {
            OrderStatus currentStatus = findOrderStatus(orderId);
            if (orderPaymentIntegrationValidator.isConfirmTerminal(currentStatus)) {
                return currentStatus;
            }
            orderPaymentIntegrationSleeper.sleep(orderProperties.paymentConfirmWaitIntervalMs());
        }
        return findOrderStatus(orderId);
    }

    public OrderEntity waitForPrepareTarget(OrderEntity order) {
        OrderEntity current = order;
        long startedAt = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startedAt) < orderProperties.paymentPrepareWaitTimeoutMs()) {
            if (!orderPaymentIntegrationValidator.shouldWaitForPrepare(current.getStatus())) {
                return current;
            }
            orderPaymentIntegrationSleeper.sleep(orderProperties.paymentPrepareWaitIntervalMs());
            entityManager.clear();
            current = findOrder(order.getId());
        }
        entityManager.clear();
        return findOrder(order.getId());
    }

    private OrderStatus findOrderStatus(Long orderId) {
        Optional<OrderStatus> current = orderRepository.findDetailById(orderId).map(OrderEntity::getStatus);
        if (current.isEmpty()) {
            throw new ErrorException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        return current.get();
    }

    private OrderEntity findOrder(Long orderId) {
        return orderRepository.findDetailById(orderId)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
    }
}

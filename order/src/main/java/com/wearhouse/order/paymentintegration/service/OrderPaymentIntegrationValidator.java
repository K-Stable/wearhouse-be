package com.wearhouse.order.paymentintegration.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.order.buyer.dto.request.OrderPaymentConfirmRequest;
import com.wearhouse.order.common.exception.OrderErrorCode;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.domain.model.PaymentMethod;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OrderPaymentIntegrationValidator {

    private static final Set<OrderStatus> PAYMENT_CONFIRMABLE_STATUSES = Set.of(
            OrderStatus.PAYMENT_PENDING,
            OrderStatus.PAYMENT_FAILED,
            OrderStatus.CONFIRMED
    );

    private static final Set<OrderStatus> PAYMENT_PREPARE_WAIT_STATUSES = Set.of(
            OrderStatus.PENDING_RESERVE
    );

    private static final Set<OrderStatus> PAYMENT_PREPARABLE_STATUSES = Set.of(
            OrderStatus.RESERVED,
            OrderStatus.PAYMENT_PENDING,
            OrderStatus.PAYMENT_FAILED,
            OrderStatus.CONFIRMED
    );

    public void validateConfirmOrder(OrderEntity order, Long buyerId, OrderPaymentConfirmRequest request) {
        if (order.getBuyerId() == null || !order.getBuyerId().equals(buyerId)) {
            throw new ErrorException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getOrderNo() == null || !order.getOrderNo().equals(request.orderNo())) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "orderNo 값이 주문 정보와 일치하지 않습니다.");
        }
        if (!PAYMENT_CONFIRMABLE_STATUSES.contains(order.getStatus())) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "결제 확정 요청 가능한 주문 상태가 아닙니다.");
        }
    }

    public void validateStablePaymentConfirmRequest(OrderPaymentConfirmRequest request) {
        if (isBlank(request.paymentKey())) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "STABLE 결제는 paymentKey 값이 필요합니다.");
        }
    }

    public void validatePrepareOrder(OrderEntity order, Long buyerId) {
        if (order.getBuyerId() == null || !order.getBuyerId().equals(buyerId)) {
            throw new ErrorException(OrderErrorCode.ORDER_NOT_FOUND);
        }
    }

    public void validatePrepareTargetStatus(OrderStatus status) {
        if (!PAYMENT_PREPARABLE_STATUSES.contains(status)) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "결제 준비 요청 가능한 주문 상태가 아닙니다.");
        }
    }

    public void validateStablePrepareTarget(OrderEntity order) {
        if (resolvePaymentMethod(order) != PaymentMethod.STABLE) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "STABLE 결제만 prepare 요청이 가능합니다.");
        }
    }

    public boolean shouldWaitForPrepare(OrderStatus status) {
        return PAYMENT_PREPARE_WAIT_STATUSES.contains(status);
    }

    public boolean isConfirmTerminal(OrderStatus status) {
        return status == OrderStatus.CONFIRMED || status == OrderStatus.PAYMENT_FAILED;
    }

    public PaymentMethod resolvePaymentMethod(OrderEntity order) {
        if (order.getOrderInfo() == null || order.getOrderInfo().getPaymentMethod() == null) {
            return PaymentMethod.CARD;
        }
        return order.getOrderInfo().getPaymentMethod();
    }

    public String resolveOrderName(OrderEntity order) {
        if (order.getOrderInfo() != null && !isBlank(order.getOrderInfo().getRecipientName())) {
            return order.getOrderInfo().getRecipientName();
        }
        return order.getOrderNo();
    }

    public String resolveCustomerKey(Long buyerId, String orderNo) {
        if (buyerId != null) {
            return "buyer:" + buyerId;
        }
        return "guest:" + orderNo;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

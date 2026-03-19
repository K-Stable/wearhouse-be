package com.wearhouse.order.domain.service.command;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.order.domain.dto.request.OrderCreateRequest;
import com.wearhouse.order.domain.dto.request.OrderPaymentConfirmRequest;
import com.wearhouse.order.domain.dto.response.OrderCreateResponse;
import com.wearhouse.order.domain.dto.response.OrderPaymentConfirmResponse;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.exception.OrderErrorCode;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.domain.model.PaymentMethod;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.infra.payment.OrderPaymentClient;
import com.wearhouse.order.infra.payment.dto.PaymentConfirmInternalRequest;
import com.wearhouse.order.infra.payment.dto.PaymentConfirmInternalResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderCheckoutOrchestrationService {

    private final OrderCommandService orderCommandService;
    private final OrderRepository orderRepository;
    private final OrderPaymentClient orderPaymentClient;
    @Value("${wearhouse.order.internal.shared-secret:wearhouse-order-internal-secret}")
    private String orderInternalSharedSecret;
    @Value("${wearhouse.order.reserve-wait-timeout-ms:2000}")
    private long reserveWaitTimeoutMs;
    @Value("${wearhouse.order.reserve-wait-interval-ms:100}")
    private long reserveWaitIntervalMs;
    @Value("${wearhouse.order.payment-confirm-wait-timeout-ms:3000}")
    private long paymentConfirmWaitTimeoutMs;
    @Value("${wearhouse.order.payment-confirm-wait-interval-ms:100}")
    private long paymentConfirmWaitIntervalMs;

    public OrderCreateResponse createOrder(OrderCreateRequest request) {
        OrderCreateResponse created = orderCommandService.createOrder(request);
        OrderStatus reservedStatus = waitForReservation(created.orderId(), request.paymentMethod());
        return created.withStatus(reservedStatus.name());
    }

    public OrderPaymentConfirmResponse confirmStablepayPayment(
            Long buyerId,
            String orderNo,
            OrderPaymentConfirmRequest request
    ) {
        OrderEntity order = orderRepository.findDetailByOrderNo(orderNo)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
        validateConfirmOrder(order, buyerId, request);

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            return new OrderPaymentConfirmResponse(order.getId(), order.getOrderNo(), OrderStatus.CONFIRMED.name(), null);
        }
        if (order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            return new OrderPaymentConfirmResponse(
                    order.getId(),
                    order.getOrderNo(),
                    OrderStatus.PAYMENT_FAILED.name(),
                    order.getFailReasonCode()
            );
        }

        ApiResponse<PaymentConfirmInternalResponse> confirmResponse = orderPaymentClient.confirmStablepayPayment(
                orderInternalSharedSecret,
                new PaymentConfirmInternalRequest(
                        request.orderId(),
                        orderNo,
                        request.paymentKey(),
                        request.amount()
                )
        );
        PaymentConfirmInternalResponse data = confirmResponse == null ? null : confirmResponse.data();
        String paymentStatus = data == null || data.paymentStatus() == null ? "" : data.paymentStatus();

        OrderStatus status = waitForConfirmResult(order.getId(), paymentStatus);
        String reasonCode = orderRepository.findDetailById(order.getId()).map(OrderEntity::getFailReasonCode).orElse(null);
        return new OrderPaymentConfirmResponse(order.getId(), orderNo, status.name(), reasonCode);
    }

    private OrderStatus waitForReservation(Long orderId, PaymentMethod paymentMethod) {
        long startedAt = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startedAt) < reserveWaitTimeoutMs) {
            Optional<OrderStatus> current = orderRepository.findDetailById(orderId).map(order -> order.getStatus());
            if (current.isEmpty()) {
                throw new ErrorException(OrderErrorCode.ORDER_NOT_FOUND);
            }
            OrderStatus currentStatus = current.get();
            if (currentStatus == OrderStatus.RESERVE_FAILED) {
                throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "재고 예약에 실패했습니다.");
            }
            if (isReservationReadyStatus(currentStatus, paymentMethod)) {
                return currentStatus;
            }
            sleepQuietly();
        }
        throw new ErrorException(OrderErrorCode.INVENTORY_PREVIEW_UNAVAILABLE, "재고 예약 처리 대기 시간이 초과되었습니다.");
    }

    private OrderStatus waitForConfirmResult(Long orderId, String paymentStatus) {
        if ("PENDING".equalsIgnoreCase(paymentStatus)) {
            return orderRepository.findDetailById(orderId)
                    .map(OrderEntity::getStatus)
                    .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
        }

        long startedAt = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startedAt) < paymentConfirmWaitTimeoutMs) {
            Optional<OrderStatus> current = orderRepository.findDetailById(orderId).map(OrderEntity::getStatus);
            if (current.isEmpty()) {
                throw new ErrorException(OrderErrorCode.ORDER_NOT_FOUND);
            }
            OrderStatus currentStatus = current.get();
            if (currentStatus == OrderStatus.CONFIRMED || currentStatus == OrderStatus.PAYMENT_FAILED) {
                return currentStatus;
            }
            sleepQuietly(paymentConfirmWaitIntervalMs);
        }
        return orderRepository.findDetailById(orderId)
                .map(OrderEntity::getStatus)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private boolean isReservationReadyStatus(OrderStatus currentStatus, PaymentMethod paymentMethod) {
        if (paymentMethod == PaymentMethod.STABLEPAY) {
            return currentStatus == OrderStatus.PAYMENT_PENDING
                    || currentStatus == OrderStatus.PAYMENT_FAILED
                    || currentStatus == OrderStatus.PAID
                    || currentStatus == OrderStatus.CONFIRMED;
        }
        return currentStatus == OrderStatus.RESERVED
                || currentStatus == OrderStatus.PAYMENT_PENDING
                || currentStatus == OrderStatus.PAYMENT_FAILED
                || currentStatus == OrderStatus.PAID
                || currentStatus == OrderStatus.CONFIRMED;
    }

    private void validateConfirmOrder(OrderEntity order, Long buyerId, OrderPaymentConfirmRequest request) {
        if (order.getBuyerId() == null || !order.getBuyerId().equals(buyerId)) {
            throw new ErrorException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getId().equals(request.orderId())) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "orderId 값이 주문 정보와 일치하지 않습니다.");
        }
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING
                && order.getStatus() != OrderStatus.PAYMENT_FAILED
                && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "결제 확정 요청 가능한 주문 상태가 아닙니다.");
        }
    }

    private void sleepQuietly() {
        sleepQuietly(reserveWaitIntervalMs);
    }

    private void sleepQuietly(long intervalMs) {
        try {
            Thread.sleep(Math.max(10, intervalMs));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ErrorException(OrderErrorCode.INVENTORY_PREVIEW_UNAVAILABLE, "재고 예약 처리 대기 중 인터럽트가 발생했습니다.");
        }
    }
}

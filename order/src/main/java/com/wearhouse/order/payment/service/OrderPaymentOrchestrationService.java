package com.wearhouse.order.payment.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.order.buyer.dto.request.OrderPaymentConfirmRequest;
import com.wearhouse.order.buyer.dto.response.OrderPaymentConfirmResponse;
import com.wearhouse.order.buyer.dto.response.OrderPaymentPrepareResponse;
import com.wearhouse.order.common.exception.OrderErrorCode;
import com.wearhouse.order.common.util.OrderIdGenerator;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.entity.OrderStatusHistoryEntity;
import com.wearhouse.order.domain.model.OrderSagaState;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.domain.model.PaymentMethod;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.infra.jpa.repository.OrderSagaRepository;
import com.wearhouse.order.infra.jpa.repository.OrderStatusHistoryRepository;
import com.wearhouse.order.payment.client.OrderPaymentClient;
import com.wearhouse.order.payment.dto.request.PaymentConfirmInternalRequest;
import com.wearhouse.order.payment.dto.request.PaymentPrepareInternalRequest;
import com.wearhouse.order.payment.dto.response.PaymentConfirmInternalResponse;
import com.wearhouse.order.payment.dto.response.PaymentPrepareInternalResponse;
import com.wearhouse.order.support.config.OrderProperties;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderPaymentOrchestrationService {

    private static final String REASON_PAYMENT_PREPARED = "PAYMENT_PREPARED";

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderSagaRepository orderSagaRepository;
    private final OrderPaymentClient orderPaymentClient;
    private final OrderProperties orderProperties;
    private final EntityManager entityManager;

    public OrderPaymentConfirmResponse confirmPayment(
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

        PaymentMethod paymentMethod = resolvePaymentMethod(order);
        if (paymentMethod != PaymentMethod.STABLE) {
            OrderStatus status = waitForConfirmResult(order.getId(), "PENDING");
            String reasonCode = orderRepository.findDetailById(order.getId()).map(OrderEntity::getFailReasonCode).orElse(null);
            return new OrderPaymentConfirmResponse(order.getId(), orderNo, status.name(), reasonCode);
        }

        validateStablePaymentConfirmRequest(request);
        ApiResponse<PaymentConfirmInternalResponse> confirmResponse = orderPaymentClient.confirmStablepayPayment(
                orderProperties.getInternal().getSharedSecret(),
                new PaymentConfirmInternalRequest(
                        order.getId(),
                        request.orderId(),
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

    public OrderPaymentPrepareResponse preparePayment(
            Long buyerId,
            String orderNo,
            String idempotencyKey
    ) {
        OrderEntity order = orderRepository.findDetailByOrderNo(orderNo)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
        validatePrepareOrder(order, buyerId);

        OrderEntity prepareTarget = waitForPrepareTarget(order);
        validatePrepareTargetStatus(prepareTarget.getStatus());
        if (prepareTarget.getStatus() == OrderStatus.PAYMENT_FAILED) {
            return new OrderPaymentPrepareResponse(null, null, null, null);
        }

        validateStablePrepareTarget(prepareTarget);
        String customerKey = resolveCustomerKey(prepareTarget.getBuyerId(), prepareTarget.getOrderNo());
        String customerId = UUID.nameUUIDFromBytes(customerKey.getBytes(StandardCharsets.UTF_8)).toString();

        ApiResponse<PaymentPrepareInternalResponse> prepareResponse = orderPaymentClient.prepareStablepayPayment(
                orderProperties.getInternal().getSharedSecret(),
                new PaymentPrepareInternalRequest(
                        prepareTarget.getId(),
                        prepareTarget.getOrderNo(),
                        customerId,
                        resolveOrderName(prepareTarget),
                        prepareTarget.getTotalAmount(),
                        buildPaymentPrepareSuccessUrl(prepareTarget.getOrderNo()),
                        buildPaymentPrepareFailUrl(prepareTarget.getOrderNo()),
                        idempotencyKey
                )
        );

        PaymentPrepareInternalResponse data = prepareResponse == null ? null : prepareResponse.data();
        if (data == null) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "결제 준비 응답이 비어 있습니다.");
        }
        markPaymentPendingAfterPrepare(prepareTarget);

        return new OrderPaymentPrepareResponse(
                data.checkoutSessionId(),
                data.checkoutUrl(),
                data.appLaunchUrl(),
                data.checkoutExpiresAt()
        );
    }

    private OrderStatus waitForConfirmResult(Long orderId, String paymentStatus) {
        if ("PENDING".equalsIgnoreCase(paymentStatus)) {
            return orderRepository.findDetailById(orderId)
                    .map(OrderEntity::getStatus)
                    .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
        }

        long startedAt = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startedAt) < orderProperties.getPaymentConfirmWaitTimeoutMs()) {
            Optional<OrderStatus> current = orderRepository.findDetailById(orderId).map(OrderEntity::getStatus);
            if (current.isEmpty()) {
                throw new ErrorException(OrderErrorCode.ORDER_NOT_FOUND);
            }
            OrderStatus currentStatus = current.get();
            if (currentStatus == OrderStatus.CONFIRMED || currentStatus == OrderStatus.PAYMENT_FAILED) {
                return currentStatus;
            }
            pause(orderProperties.getPaymentConfirmWaitIntervalMs());
        }
        return orderRepository.findDetailById(orderId)
                .map(OrderEntity::getStatus)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private OrderEntity waitForPrepareTarget(OrderEntity order) {
        OrderEntity current = order;
        long startedAt = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startedAt) < orderProperties.getPaymentPrepareWaitTimeoutMs()) {
            if (!OrderStatusPolicy.PAYMENT_PREPARE_WAIT_STATUSES.contains(current.getStatus())) {
                return current;
            }
            pause(orderProperties.getPaymentPrepareWaitIntervalMs());
            entityManager.clear();
            current = orderRepository.findDetailById(order.getId())
                    .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
        }
        entityManager.clear();
        return orderRepository.findDetailById(order.getId())
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private void validateConfirmOrder(OrderEntity order, Long buyerId, OrderPaymentConfirmRequest request) {
        if (order.getBuyerId() == null || !order.getBuyerId().equals(buyerId)) {
            throw new ErrorException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getOrderNo() == null || !order.getOrderNo().equals(request.orderId())) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "orderId 값이 주문 정보와 일치하지 않습니다.");
        }
        if (!OrderStatusPolicy.PAYMENT_CONFIRMABLE_STATUSES.contains(order.getStatus())) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "결제 확정 요청 가능한 주문 상태가 아닙니다.");
        }
    }

    private void validateStablePaymentConfirmRequest(OrderPaymentConfirmRequest request) {
        if (isBlank(request.paymentKey())) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "STABLE 결제는 paymentKey 값이 필요합니다.");
        }
    }

    private void validatePrepareOrder(OrderEntity order, Long buyerId) {
        if (order.getBuyerId() == null || !order.getBuyerId().equals(buyerId)) {
            throw new ErrorException(OrderErrorCode.ORDER_NOT_FOUND);
        }
    }

    private void validatePrepareTargetStatus(OrderStatus status) {
        if (!OrderStatusPolicy.PAYMENT_PREPARABLE_STATUSES.contains(status)) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "결제 준비 요청 가능한 주문 상태가 아닙니다.");
        }
    }

    private void validateStablePrepareTarget(OrderEntity order) {
        PaymentMethod paymentMethod = resolvePaymentMethod(order);
        if (paymentMethod != PaymentMethod.STABLE) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "STABLE 결제만 prepare 요청이 가능합니다.");
        }
    }

    private void pause(long intervalMs) {
        try {
            Thread.sleep(Math.max(10, intervalMs));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ErrorException(OrderErrorCode.INVENTORY_PREVIEW_UNAVAILABLE, "주문 상태 대기 중 인터럽트가 발생했습니다.");
        }
    }

    private String buildPaymentPrepareSuccessUrl(String orderNo) {
        return replaceOrderNoTemplate(orderProperties.getPaymentPrepareSuccessUrlTemplate(), orderNo);
    }

    private String buildPaymentPrepareFailUrl(String orderNo) {
        return replaceOrderNoTemplate(orderProperties.getPaymentPrepareFailUrlTemplate(), orderNo);
    }

    private String replaceOrderNoTemplate(String template, String orderNo) {
        if (template == null) {
            return "";
        }
        return template.replace("{orderNo}", orderNo);
    }

    private String resolveOrderName(OrderEntity order) {
        if (order.getOrderInfo() != null && !isBlank(order.getOrderInfo().getRecipientName())) {
            return order.getOrderInfo().getRecipientName();
        }
        return order.getOrderNo();
    }

    private void markPaymentPendingAfterPrepare(OrderEntity order) {
        if (order.getStatus() != OrderStatus.RESERVED) {
            return;
        }
        String eventId = OrderIdGenerator.newEventId();
        order.updateStatus(OrderStatus.PAYMENT_PENDING, null, null, null);
        orderRepository.save(order);
        saveStatusHistory(order, OrderStatus.RESERVED, OrderStatus.PAYMENT_PENDING, eventId, REASON_PAYMENT_PREPARED);
        transitionSaga(order.getId(), OrderSagaState.WAITING_PAYMENT_RESULT, eventId, null);
    }

    private void saveStatusHistory(
            OrderEntity order,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String eventId,
            String reasonCode
    ) {
        orderStatusHistoryRepository.save(OrderStatusHistoryEntity.create(
                order,
                fromStatus,
                toStatus,
                eventId,
                reasonCode
        ));
    }

    private void transitionSaga(
            Long orderId,
            OrderSagaState nextState,
            String eventId,
            String failReasonCode
    ) {
        orderSagaRepository.findByOrder_Id(orderId)
                .ifPresent(saga -> saga.transition(nextState, eventId, failReasonCode));
    }

    private String resolveCustomerKey(Long buyerId, String orderNo) {
        if (buyerId != null) {
            return "buyer:" + buyerId;
        }
        return "guest:" + orderNo;
    }

    private PaymentMethod resolvePaymentMethod(OrderEntity order) {
        if (order.getOrderInfo() == null || order.getOrderInfo().getPaymentMethod() == null) {
            return PaymentMethod.CARD;
        }
        return order.getOrderInfo().getPaymentMethod();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class OrderStatusPolicy {
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
    }
}


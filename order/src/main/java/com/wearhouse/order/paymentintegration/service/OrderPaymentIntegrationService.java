package com.wearhouse.order.paymentintegration.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.order.buyer.dto.request.OrderPaymentConfirmRequest;
import com.wearhouse.order.buyer.dto.response.OrderPaymentConfirmResponse;
import com.wearhouse.order.buyer.dto.response.OrderPaymentPrepareResponse;
import com.wearhouse.order.common.event.OrderEventType;
import com.wearhouse.order.common.exception.OrderErrorCode;
import com.wearhouse.order.common.util.OrderIdGenerator;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.entity.OrderSagaHistoryEntity;
import com.wearhouse.order.domain.entity.OrderStatusHistoryEntity;
import com.wearhouse.order.domain.model.OrderSagaState;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.domain.model.PaymentMethod;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.infra.jpa.repository.OrderSagaHistoryRepository;
import com.wearhouse.order.infra.jpa.repository.OrderSagaRepository;
import com.wearhouse.order.infra.jpa.repository.OrderStatusHistoryRepository;
import com.wearhouse.order.kafka.dto.PaymentEventPayload;
import com.wearhouse.order.paymentintegration.mapper.OrderPaymentIntegrationResponseMapper;
import com.wearhouse.order.paymentintegration.dto.response.PaymentConfirmInternalResponse;
import com.wearhouse.order.paymentintegration.dto.response.PaymentPrepareInternalResponse;
import com.wearhouse.order.saga.service.OrderSagaService;
import com.wearhouse.order.support.monitoring.OrderFlowMetrics;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderPaymentIntegrationService {

    private static final String REASON_PAYMENT_PREPARED = "PAYMENT_PREPARED";
    private static final String INVENTORY_OUT_OF_STOCK_REASON = "INVENTORY_409_001";
    private static final String INVENTORY_LOCK_FAILED_REASON = "INVENTORY_409_003";

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderSagaRepository orderSagaRepository;
    private final OrderSagaHistoryRepository orderSagaHistoryRepository;
    private final OrderPaymentIntegrationResponseMapper orderPaymentIntegrationResponseMapper;
    private final OrderPaymentIntegrationValidator orderPaymentIntegrationValidator;
    private final OrderPaymentIntegrationGatewayAdapter orderPaymentIntegrationGatewayAdapter;
    private final OrderSagaService orderSagaService;
    private final OrderFlowMetrics orderFlowMetrics;

    public OrderPaymentConfirmResponse confirmPayment(
            Long buyerId,
            String orderNo,
            OrderPaymentConfirmRequest request
    ) {
        OrderEntity order = orderRepository.findDetailByOrderNo(orderNo)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
        orderPaymentIntegrationValidator.validateConfirmOrder(order, buyerId, request);

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            return orderPaymentIntegrationResponseMapper.toConfirmResponse(order, OrderStatus.CONFIRMED, null);
        }
        if (order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            return orderPaymentIntegrationResponseMapper.toConfirmResponse(
                    order,
                    OrderStatus.PAYMENT_FAILED,
                    order.getFailReasonCode()
            );
        }

        PaymentMethod paymentMethod = orderPaymentIntegrationValidator.resolvePaymentMethod(order);
        if (paymentMethod != PaymentMethod.STABLE) {
            return orderPaymentIntegrationResponseMapper.toConfirmResponse(
                    order,
                    order.getStatus(),
                    order.getFailReasonCode()
            );
        }

        orderPaymentIntegrationValidator.validateStablePaymentConfirmRequest(request);
        PaymentConfirmInternalResponse confirmResponse = orderPaymentIntegrationGatewayAdapter.requestStableConfirm(order, request);
        OrderStatus status = applyConfirmResult(order, confirmResponse);
        String reasonCode = findFailReasonCode(order.getId());
        return orderPaymentIntegrationResponseMapper.toConfirmResponse(
                order,
                status,
                reasonCode == null ? confirmResponse.reasonCode() : reasonCode
        );
    }

    public OrderPaymentPrepareResponse preparePayment(
            Long buyerId,
            String orderNo,
            String idempotencyKey
    ) {
        OrderEntity order = orderRepository.findDetailByOrderNo(orderNo)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
        orderPaymentIntegrationValidator.validatePrepareOrder(order, buyerId);

        OrderEntity prepareTarget = order;
        throwIfReserveFailed(prepareTarget);
        orderPaymentIntegrationValidator.validatePrepareTargetStatus(prepareTarget.getStatus());
        if (prepareTarget.getStatus() == OrderStatus.PAYMENT_FAILED) {
            return orderPaymentIntegrationResponseMapper.toEmptyPrepareResponse();
        }

        orderPaymentIntegrationValidator.validateStablePrepareTarget(prepareTarget);
        String customerKey = orderPaymentIntegrationValidator.resolveCustomerKey(prepareTarget.getBuyerId(), prepareTarget.getOrderNo());
        String customerId = UUID.nameUUIDFromBytes(customerKey.getBytes(StandardCharsets.UTF_8)).toString();

        PaymentPrepareInternalResponse data = orderPaymentIntegrationGatewayAdapter.requestStablePrepare(
                prepareTarget,
                customerId,
                orderPaymentIntegrationValidator.resolveOrderName(prepareTarget),
                idempotencyKey
        );
        markPaymentPendingAfterPrepare(prepareTarget);

        return orderPaymentIntegrationResponseMapper.toPrepareResponse(data);
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
        orderStatusHistoryRepository.save(OrderStatusHistoryEntity.of(
                order,
                fromStatus,
                toStatus,
                eventId,
                reasonCode
        ));
        orderFlowMetrics.recordStatusTransition(fromStatus, toStatus, reasonCode);
    }

    private void transitionSaga(
            Long orderId,
            OrderSagaState nextState,
            String eventId,
            String failReasonCode
    ) {
        orderSagaRepository.findByOrder_Id(orderId)
                .ifPresent(saga -> {
                    OrderSagaState fromState = saga.getState();
                    saga.transition(nextState, eventId, failReasonCode);
                    orderSagaHistoryRepository.save(OrderSagaHistoryEntity.of(
                            saga.getOrder(),
                            fromState,
                            nextState,
                            eventId,
                            failReasonCode
                    ));
                    orderFlowMetrics.recordSagaTransition(fromState, nextState, failReasonCode);
                });
    }

    private String findFailReasonCode(Long orderId) {
        return orderRepository.findDetailById(orderId)
                .map(OrderEntity::getFailReasonCode)
                .orElse(null);
    }

    private OrderStatus applyConfirmResult(OrderEntity order, PaymentConfirmInternalResponse confirmResponse) {
        String paymentStatus = normalizeStatus(confirmResponse.paymentStatus());
        if ("AUTHORIZED".equals(paymentStatus)) {
            relayPaymentEventToSaga(order, OrderEventType.PAYMENT_AUTHORIZED, null);
            return findOrderStatus(order.getId());
        }
        if ("FAILED".equals(paymentStatus)) {
            relayPaymentEventToSaga(order, OrderEventType.PAYMENT_FAILED, confirmResponse.reasonCode());
            return findOrderStatus(order.getId());
        }
        return findOrderStatus(order.getId());
    }

    private void relayPaymentEventToSaga(OrderEntity order, String eventType, String reasonCode) {
        orderSagaService.onPaymentEvent(
                OrderIdGenerator.newEventId(),
                eventType,
                "internal/order-payment-confirm",
                String.valueOf(order.getId()),
                new PaymentEventPayload(
                        order.getId(),
                        order.getOrderNo(),
                        reasonCode
                )
        );
    }

    private OrderStatus findOrderStatus(Long orderId) {
        return orderRepository.findDetailById(orderId)
                .map(OrderEntity::getStatus)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        return status.trim().toUpperCase();
    }

    private void throwIfReserveFailed(OrderEntity order) {
        if (order.getStatus() != OrderStatus.RESERVE_FAILED) {
            return;
        }

        String reasonCode = normalizeStatus(order.getFailReasonCode());
        if (INVENTORY_OUT_OF_STOCK_REASON.equals(reasonCode)) {
            throw new ErrorException(
                    OrderErrorCode.INVENTORY_OUT_OF_STOCK,
                    "재고가 부족하여 주문 예약에 실패했습니다. 수량을 확인해 주세요."
            );
        }
        if (INVENTORY_LOCK_FAILED_REASON.equals(reasonCode)) {
            throw new ErrorException(
                    OrderErrorCode.INVENTORY_LOCK_FAILED,
                    "요청이 몰려 재고 예약에 실패했습니다. 잠시 후 다시 시도해 주세요."
            );
        }
        throw new ErrorException(
                OrderErrorCode.INVENTORY_RESERVE_FAILED,
                "재고 예약에 실패했습니다."
        );
    }
}

package com.wearhouse.order.paymentintegration.service;

import com.wearhouse.common.global.error.ErrorException;
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
import com.wearhouse.order.paymentintegration.mapper.OrderPaymentIntegrationResponseMapper;
import com.wearhouse.order.paymentintegration.dto.response.PaymentPrepareInternalResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderPaymentIntegrationService {

    private static final String REASON_PAYMENT_PREPARED = "PAYMENT_PREPARED";

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderSagaRepository orderSagaRepository;
    private final OrderPaymentIntegrationResponseMapper orderPaymentIntegrationResponseMapper;
    private final OrderPaymentIntegrationValidator orderPaymentIntegrationValidator;
    private final OrderPaymentIntegrationWaiter orderPaymentIntegrationWaiter;
    private final OrderPaymentIntegrationGatewayAdapter orderPaymentIntegrationGatewayAdapter;

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
            OrderStatus status = orderPaymentIntegrationWaiter.waitForConfirmResult(order.getId(), "PENDING");
            String reasonCode = findFailReasonCode(order.getId());
            return orderPaymentIntegrationResponseMapper.toConfirmResponse(order, status, reasonCode);
        }

        orderPaymentIntegrationValidator.validateStablePaymentConfirmRequest(request);
        String paymentStatus = orderPaymentIntegrationGatewayAdapter.requestStableConfirmStatus(order, request);
        OrderStatus status = orderPaymentIntegrationWaiter.waitForConfirmResult(order.getId(), paymentStatus);
        String reasonCode = findFailReasonCode(order.getId());
        return orderPaymentIntegrationResponseMapper.toConfirmResponse(order, status, reasonCode);
    }

    public OrderPaymentPrepareResponse preparePayment(
            Long buyerId,
            String orderNo,
            String idempotencyKey
    ) {
        OrderEntity order = orderRepository.findDetailByOrderNo(orderNo)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
        orderPaymentIntegrationValidator.validatePrepareOrder(order, buyerId);

        OrderEntity prepareTarget = orderPaymentIntegrationWaiter.waitForPrepareTarget(order);
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

    private String findFailReasonCode(Long orderId) {
        return orderRepository.findDetailById(orderId)
                .map(OrderEntity::getFailReasonCode)
                .orElse(null);
    }
}

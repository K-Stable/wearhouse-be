package com.wearhouse.order.domain.service.command;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.entity.OrderStatusHistoryEntity;
import com.wearhouse.order.domain.event.OrderDomainEvent;
import com.wearhouse.order.domain.event.OrderDomainEventPublisher;
import com.wearhouse.order.domain.event.OrderEventType;
import com.wearhouse.order.domain.exception.OrderErrorCode;
import com.wearhouse.order.domain.model.OrderSagaState;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.infra.jpa.repository.OrderInboxRepository;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.infra.jpa.repository.OrderSagaRepository;
import com.wearhouse.order.infra.jpa.repository.OrderStatusHistoryRepository;
import com.wearhouse.order.support.OrderIdGenerator;
import com.wearhouse.order.support.config.OrderKafkaTopicsProperties;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.wearhouse.common.global.transactional.WriteTx;

@RequiredArgsConstructor
@Service
public class OrderSagaService {

    private static final String AGGREGATE_TYPE_ORDER = "ORDER";
    private static final String DEFAULT_PAYMENT_FAIL_REASON = "PAYMENT_FAILED";
    private static final String DEFAULT_STOCK_RESERVE_FAIL_REASON = "STOCK_RESERVE_FAILED";
    private static final String INVENTORY_CONSUMER = "order-inventory-consumer";
    private static final String PAYMENT_CONSUMER = "order-payment-consumer";

    private final OrderRepository orderRepository;
    private final OrderSagaRepository orderSagaRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderInboxRepository orderInboxRepository;
    private final OrderDomainEventPublisher orderDomainEventPublisher;
    private final OrderKafkaTopicsProperties kafkaTopicsProperties;
    private final Map<String, SagaEventHandler> inventoryEventHandlers = Map.of(
            OrderEventType.STOCK_RESERVED,
            this::handleStockReservedEvent,
            OrderEventType.STOCK_RESERVE_FAILED,
            this::handleStockReserveFailedEvent,
            OrderEventType.INVENTORY_RELEASED,
            this::handleInventoryReleasedEvent
    );
    private final Map<String, SagaEventHandler> paymentEventHandlers = Map.of(
            OrderEventType.PAYMENT_AUTHORIZED,
            this::handlePaymentAuthorizedEvent,
            OrderEventType.PAYMENT_FAILED,
            this::handlePaymentFailedEvent
    );


    @WriteTx
    public void onInventoryEvent(
            String eventId,
            String eventType,
            String topic,
            String partitionKey,
            String rawPayload,
            Long orderId,
            Map<String, Object> payload
    ) {
        processEvent(
                INVENTORY_CONSUMER,
                eventId,
                eventType,
                topic,
                partitionKey,
                rawPayload,
                orderId,
                payload,
                inventoryEventHandlers
        );
    }

    @WriteTx
    public void onPaymentEvent(
            String eventId,
            String eventType,
            String topic,
            String partitionKey,
            String rawPayload,
            Long orderId,
            Map<String, Object> payload
    ) {
        processEvent(
                PAYMENT_CONSUMER,
                eventId,
                eventType,
                topic,
                partitionKey,
                rawPayload,
                orderId,
                payload,
                paymentEventHandlers
        );
    }

    private void processEvent(
            String consumerName,
            String eventId,
            String eventType,
            String topic,
            String partitionKey,
            String rawPayload,
            Long orderId,
            Map<String, Object> payload,
            Map<String, SagaEventHandler> handlers
    ) {
        // Inbox로 중복 소비를 차단한다. (이미 처리된 eventId는 즉시 무시)
        if (!orderInboxRepository.tryReceive(eventId, consumerName, eventType, topic, partitionKey, rawPayload)) {
            return;
        }

        try {
            OrderEntity order = loadOrder(orderId);
            OrderStatus currentStatus = order.getStatus();
            dispatchEvent(handlers, eventType, order, currentStatus, eventId, payload);
            orderInboxRepository.markProcessed(eventId, consumerName);
        } catch (Exception exception) {
            orderInboxRepository.markFailed(eventId, consumerName, "CONSUME_FAIL", exception.getMessage());
            throw exception;
        }
    }

    private OrderEntity loadOrder(Long orderId) {
        if (orderId == null) {
            throw new ErrorException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        return orderRepository.findDetailById(orderId)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private void handleStockReserved(OrderEntity order, OrderStatus currentStatus, String eventId) {
        // 결제 실패 후 재고예약 이벤트가 지연 도착한 경우: 즉시 보상(해제)으로 수렴시킨다.
        if (currentStatus == OrderStatus.PAYMENT_FAILED) {
            order.markItemsReserved();
            publishInventoryReleaseRequested(order, asString(order.getFailReasonCode(), DEFAULT_PAYMENT_FAIL_REASON));
            transitionSaga(order.getId(), OrderSagaState.COMPENSATING, eventId, order.getFailReasonCode());
            return;
        }
        if (currentStatus != OrderStatus.PENDING_RESERVE) {
            return;
        }

        // 정상 경로: 예약 성공 -> RESERVED 전이 -> 결제 준비 이벤트 발행
        order.updateStatus(OrderStatus.RESERVED, null, null, null);
        order.markItemsReserved();
        saveStatusHistory(order, currentStatus, OrderStatus.RESERVED, eventId, "STOCK_RESERVED");
        transitionSaga(order.getId(), OrderSagaState.WAITING_PAYMENT_PREPARE, eventId, null);
        publishPaymentPrepareRequested(order);
    }

    private void handleStockReserveFailed(
            OrderEntity order,
            OrderStatus currentStatus,
            String eventId,
            Map<String, Object> payload
    ) {
        if (currentStatus != OrderStatus.PENDING_RESERVE) {
            return;
        }

        // 재고예약 실패는 주문을 RESERVE_FAILED로 종료한다.
        String reasonCode = asString(payload.get("reasonCode"), DEFAULT_STOCK_RESERVE_FAIL_REASON);
        order.updateStatus(OrderStatus.RESERVE_FAILED, reasonCode, null, null);
        saveStatusHistory(order, currentStatus, OrderStatus.RESERVE_FAILED, eventId, reasonCode);
        transitionSaga(order.getId(), OrderSagaState.RESERVE_FAILED, eventId, reasonCode);
    }

    private void handleInventoryReleased(OrderEntity order, OrderStatus currentStatus, String eventId) {
        if (currentStatus != OrderStatus.PAYMENT_FAILED) {
            return;
        }

        order.markItemsPendingReserve();
        transitionSaga(order.getId(), OrderSagaState.FAILED, eventId, order.getFailReasonCode());
    }

    private void handlePaymentAuthorized(OrderEntity order, OrderStatus currentStatus, String eventId) {
        if (currentStatus != OrderStatus.PAYMENT_PENDING) {
            return;
        }

        // 결제 승인 성공 시 주문을 PAID -> CONFIRMED로 전이하고 후속 확정 이벤트를 발행한다.
        order.updateStatus(OrderStatus.PAID, null, null, null);
        saveStatusHistory(order, currentStatus, OrderStatus.PAID, eventId, "PAYMENT_AUTHORIZED");

        order.updateStatus(OrderStatus.CONFIRMED, null, LocalDateTime.now(), null);
        order.markItemsConfirmed();
        saveStatusHistory(order, OrderStatus.PAID, OrderStatus.CONFIRMED, eventId, "ORDER_CONFIRMED");
        transitionSaga(order.getId(), OrderSagaState.CONFIRMED, eventId, null);
        publishOrderConfirmed(order);
    }

    private void handlePaymentFailed(
            OrderEntity order,
            OrderStatus currentStatus,
            String eventId,
            Map<String, Object> payload
    ) {
        if (currentStatus != OrderStatus.PENDING_RESERVE
                && currentStatus != OrderStatus.RESERVED
                && currentStatus != OrderStatus.PAYMENT_PENDING) {
            return;
        }

        // 결제 실패 시 PAYMENT_FAILED 전이 후, 재고가 이미 예약된 주문은 보상 흐름으로 보낸다.
        String reasonCode = asString(payload.get("reasonCode"), DEFAULT_PAYMENT_FAIL_REASON);
        order.updateStatus(OrderStatus.PAYMENT_FAILED, reasonCode, null, null);
        saveStatusHistory(order, currentStatus, OrderStatus.PAYMENT_FAILED, eventId, reasonCode);

        if (currentStatus == OrderStatus.RESERVED || currentStatus == OrderStatus.PAYMENT_PENDING) {
            transitionSaga(order.getId(), OrderSagaState.COMPENSATING, eventId, reasonCode);
            publishInventoryReleaseRequested(order, reasonCode);
            return;
        }
        transitionSaga(order.getId(), OrderSagaState.FAILED, eventId, reasonCode);
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

    private void publishPaymentPrepareRequested(OrderEntity order) {
        String eventId = OrderIdGenerator.newEventId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("buyerId", order.getBuyerId());
        payload.put("amount", order.getTotalAmount());
        payload.put(
                "paymentMethod",
                order.getOrderInfo() == null || order.getOrderInfo().getPaymentMethod() == null
                        ? null
                        : order.getOrderInfo().getPaymentMethod().name()
        );

        OrderDomainEvent event = OrderDomainEvent.builder()
                .eventId(eventId)
                .eventType(OrderEventType.PAYMENT_PREPARE_REQUESTED)
                .aggregateType(AGGREGATE_TYPE_ORDER)
                .aggregateId(String.valueOf(order.getId()))
                .topic(kafkaTopicsProperties.getPaymentPrepareTopic())
                .partitionKey(String.valueOf(order.getId()))
                .payload(payload)
                .build();
        orderDomainEventPublisher.publish(event);

        // 결제 준비 요청 이벤트 발행 직후 상태를 PAYMENT_PENDING으로 전이한다.
        order.updateStatus(OrderStatus.PAYMENT_PENDING, null, null, null);
        saveStatusHistory(order, OrderStatus.RESERVED, OrderStatus.PAYMENT_PENDING, eventId, "PAYMENT_PREPARE_REQUESTED");
        transitionSaga(order.getId(), OrderSagaState.WAITING_PAYMENT_RESULT, eventId, null);
    }

    private void publishInventoryReleaseRequested(OrderEntity order, String reasonCode) {
        String eventId = OrderIdGenerator.newEventId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("reasonCode", reasonCode);

        OrderDomainEvent event = OrderDomainEvent.builder()
                .eventId(eventId)
                .eventType(OrderEventType.INVENTORY_RELEASE_REQUESTED)
                .aggregateType(AGGREGATE_TYPE_ORDER)
                .aggregateId(String.valueOf(order.getId()))
                .topic(kafkaTopicsProperties.getInventoryCommandTopic())
                .partitionKey(String.valueOf(order.getId()))
                .payload(payload)
                .build();
        orderDomainEventPublisher.publish(event);
    }

    private void publishOrderConfirmed(OrderEntity order) {
        String eventId = OrderIdGenerator.newEventId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("buyerId", order.getBuyerId());
        payload.put("confirmedAt", LocalDateTime.now());

        OrderDomainEvent event = OrderDomainEvent.builder()
                .eventId(eventId)
                .eventType(OrderEventType.ORDER_CONFIRMED)
                .aggregateType(AGGREGATE_TYPE_ORDER)
                .aggregateId(String.valueOf(order.getId()))
                .topic(kafkaTopicsProperties.getOrderEventTopic())
                .partitionKey(String.valueOf(order.getId()))
                .payload(payload)
                .build();
        orderDomainEventPublisher.publish(event);
    }

    private String asString(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private void dispatchEvent(
            Map<String, SagaEventHandler> handlers,
            String eventType,
            OrderEntity order,
            OrderStatus currentStatus,
            String eventId,
            Map<String, Object> payload
    ) {
        SagaEventHandler handler = handlers.get(eventType);
        if (handler == null) {
            return;
        }
        handler.handle(order, currentStatus, eventId, payload);
    }

    private void handleStockReservedEvent(
            OrderEntity order,
            OrderStatus currentStatus,
            String eventId,
            Map<String, Object> payload
    ) {
        handleStockReserved(order, currentStatus, eventId);
    }

    private void handleStockReserveFailedEvent(
            OrderEntity order,
            OrderStatus currentStatus,
            String eventId,
            Map<String, Object> payload
    ) {
        handleStockReserveFailed(order, currentStatus, eventId, payload);
    }

    private void handleInventoryReleasedEvent(
            OrderEntity order,
            OrderStatus currentStatus,
            String eventId,
            Map<String, Object> payload
    ) {
        handleInventoryReleased(order, currentStatus, eventId);
    }

    private void handlePaymentAuthorizedEvent(
            OrderEntity order,
            OrderStatus currentStatus,
            String eventId,
            Map<String, Object> payload
    ) {
        handlePaymentAuthorized(order, currentStatus, eventId);
    }

    private void handlePaymentFailedEvent(
            OrderEntity order,
            OrderStatus currentStatus,
            String eventId,
            Map<String, Object> payload
    ) {
        handlePaymentFailed(order, currentStatus, eventId, payload);
    }

    @FunctionalInterface
    private interface SagaEventHandler {
        void handle(
                OrderEntity order,
                OrderStatus currentStatus,
                String eventId,
                Map<String, Object> payload
        );
    }
}

package com.wearhouse.order.domain.service.command;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.entity.OrderStatusHistoryEntity;
import com.wearhouse.order.domain.event.OrderDomainEvent;
import com.wearhouse.order.domain.event.OrderDomainEventPublisher;
import com.wearhouse.order.domain.exception.OrderErrorCode;
import com.wearhouse.order.domain.model.OrderSagaState;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.infra.jpa.repository.OrderInboxRepository;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.infra.jpa.repository.OrderSagaRepository;
import com.wearhouse.order.infra.jpa.repository.OrderStatusHistoryRepository;
import com.wearhouse.order.support.OrderIdGenerator;
import com.wearhouse.common.support.lock.DistributedLock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.wearhouse.common.global.transactional.WriteTx;

@RequiredArgsConstructor
@Service
public class OrderSagaService {

    private static final String INVENTORY_CONSUMER = "order-inventory-consumer";
    private static final String PAYMENT_CONSUMER = "order-payment-consumer";

    private final OrderRepository orderRepository;
    private final OrderSagaRepository orderSagaRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderInboxRepository orderInboxRepository;
    private final OrderDomainEventPublisher orderDomainEventPublisher;
    private final @Value("${wearhouse.kafka.payment-prepare-topic:wearhouse.payment.command.v1}")String paymentPrepareTopic;
    private final @Value("${wearhouse.kafka.inventory-command-topic:wearhouse.inventory.command.v1}")String inventoryCommandTopic;
    private final @Value("${wearhouse.kafka.order-event-topic:wearhouse.order.event.v1}") String orderEventTopic;


    @WriteTx
    @DistributedLock(
            key = "#p5['orderId']",
            prefix = "order:lock:saga:"
    )
    public void onInventoryEvent(
            String eventId,
            String eventType,
            String topic,
            String partitionKey,
            String rawPayload,
            Map<String, Object> payload
    ) {
        if (!orderInboxRepository.tryReceive(eventId, INVENTORY_CONSUMER, eventType, topic, partitionKey, rawPayload)) {
            return;
        }

        try {
            OrderEntity order = loadOrder(payload);
            OrderStatus currentStatus = order.getStatus();

            switch (eventType) {
                case "StockReserved" -> handleStockReserved(order, currentStatus, eventId);
                case "StockReserveFailed" -> handleStockReserveFailed(order, currentStatus, eventId, payload);
                case "InventoryReleased" -> handleInventoryReleased(order, currentStatus, eventId);
                default -> {
                }
            }

            orderInboxRepository.markProcessed(eventId, INVENTORY_CONSUMER);
        } catch (Exception exception) {
            orderInboxRepository.markFailed(eventId, INVENTORY_CONSUMER, "CONSUME_FAIL", exception.getMessage());
            throw exception;
        }
    }

    @WriteTx
    @DistributedLock(
            key = "#p5['orderId']",
            prefix = "order:lock:saga:"
    )
    public void onPaymentEvent(
            String eventId,
            String eventType,
            String topic,
            String partitionKey,
            String rawPayload,
            Map<String, Object> payload
    ) {
        if (!orderInboxRepository.tryReceive(eventId, PAYMENT_CONSUMER, eventType, topic, partitionKey, rawPayload)) {
            return;
        }

        try {
            OrderEntity order = loadOrder(payload);
            OrderStatus currentStatus = order.getStatus();

            switch (eventType) {
                case "PaymentAuthorized" -> handlePaymentAuthorized(order, currentStatus, eventId);
                case "PaymentFailed" -> handlePaymentFailed(order, currentStatus, eventId, payload);
                default -> {
                }
            }

            orderInboxRepository.markProcessed(eventId, PAYMENT_CONSUMER);
        } catch (Exception exception) {
            orderInboxRepository.markFailed(eventId, PAYMENT_CONSUMER, "CONSUME_FAIL", exception.getMessage());
            throw exception;
        }
    }

    private OrderEntity loadOrder(Map<String, Object> payload) {
        Long orderId = asLong(payload.get("orderId"));
        return orderRepository.findDetailById(orderId)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private void handleStockReserved(OrderEntity order, OrderStatus currentStatus, String eventId) {
        if (currentStatus == OrderStatus.PAYMENT_FAILED) {
            order.markItemsReserved();
            publishInventoryReleaseRequested(order, asString(order.getFailReasonCode(), "PAYMENT_FAILED"));
            transitionSaga(order.getId(), OrderSagaState.COMPENSATING, eventId, order.getFailReasonCode());
            return;
        }
        if (currentStatus != OrderStatus.PENDING_RESERVE) {
            return;
        }

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

        String reasonCode = asString(payload.get("reasonCode"), "STOCK_RESERVE_FAILED");
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

        String reasonCode = asString(payload.get("reasonCode"), "PAYMENT_FAILED");
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
                .eventType("PaymentPrepareRequested")
                .aggregateType("ORDER")
                .aggregateId(String.valueOf(order.getId()))
                .topic(paymentPrepareTopic)
                .partitionKey(String.valueOf(order.getId()))
                .payload(payload)
                .build();
        orderDomainEventPublisher.publish(event);

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
                .eventType("InventoryReleaseRequested")
                .aggregateType("ORDER")
                .aggregateId(String.valueOf(order.getId()))
                .topic(inventoryCommandTopic)
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
                .eventType("OrderConfirmed")
                .aggregateType("ORDER")
                .aggregateId(String.valueOf(order.getId()))
                .topic(orderEventTopic)
                .partitionKey(String.valueOf(order.getId()))
                .payload(payload)
                .build();
        orderDomainEventPublisher.publish(event);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            return Long.parseLong(stringValue);
        }
        throw new IllegalArgumentException("orderId 값이 올바르지 않습니다.");
    }

    private String asString(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }
}

package com.wearhouse.order.domain.service.command;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.order.domain.dto.request.OrderCancelRequest;
import com.wearhouse.order.domain.dto.request.OrderCreateRequest;
import com.wearhouse.order.domain.dto.request.OrderCreateRequest.OrderCreateItemRequest;
import com.wearhouse.order.domain.dto.response.OrderCancelResponse;
import com.wearhouse.order.domain.dto.response.OrderCreateResponse;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.entity.OrderInfo;
import com.wearhouse.order.domain.entity.OrderSagaEntity;
import com.wearhouse.order.domain.entity.OrderStatusHistoryEntity;
import com.wearhouse.order.domain.event.OrderDomainEvent;
import com.wearhouse.order.domain.event.OrderDomainEventPublisher;
import com.wearhouse.order.domain.exception.OrderErrorCode;
import com.wearhouse.order.domain.model.OrderSagaState;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.infra.jpa.repository.OrderSagaRepository;
import com.wearhouse.order.infra.jpa.repository.OrderStatusHistoryRepository;
import com.wearhouse.order.support.OrderIdGenerator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.wearhouse.common.global.transactional.WriteTx;

@Service
@RequiredArgsConstructor
public class OrderCommandService {

    private static final Set<OrderStatus> CANCELLABLE_STATUSES = Set.of(
            OrderStatus.PENDING_RESERVE,
            OrderStatus.RESERVE_FAILED,
            OrderStatus.RESERVED,
            OrderStatus.PAYMENT_PENDING,
            OrderStatus.PAYMENT_FAILED
    );
    private static final String AGGREGATE_TYPE_ORDER = "ORDER";
    private static final String EVENT_ORDER_CREATED = "OrderCreated";
    private static final String EVENT_ORDER_CANCELLED = "OrderCancelled";
    private static final String EVENT_INVENTORY_RESERVE_REQUESTED = "InventoryReserveRequested";
    private static final String EVENT_INVENTORY_RELEASE_REQUESTED = "InventoryReleaseRequested";
    private static final String REASON_ORDER_CREATED = "ORDER_CREATED";
    private static final String DEFAULT_CANCEL_REASON = "BUYER_CANCEL";
    private static final Set<OrderStatus> RELEASE_REQUIRED_STATUSES = Set.of(
            OrderStatus.RESERVED,
            OrderStatus.PAYMENT_PENDING,
            OrderStatus.PAYMENT_FAILED
    );

    private final OrderRepository orderRepository;
    private final OrderSagaRepository orderSagaRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderDomainEventPublisher orderDomainEventPublisher;
    private final @Value("${wearhouse.kafka.inventory-reserve-topic:wearhouse.inventory.command.v1}") String inventoryReserveTopic;
    private final @Value("${wearhouse.kafka.inventory-command-topic:wearhouse.inventory.command.v1}") String inventoryCommandTopic;
    private final @Value("${wearhouse.order.payment-result-timeout-minutes:30}")int paymentResultTimeoutMinutes;


    @WriteTx
    public OrderCreateResponse createOrder(OrderCreateRequest request) {
        validateCreateRequest(request);

        OrderAmountSummary amountSummary = calculateAmountSummary(request);
        String orderNo = OrderIdGenerator.newOrderNo();
        LocalDateTime orderedAt = LocalDateTime.now();
        String eventId = OrderIdGenerator.newEventId();

        OrderEntity order = createOrderEntity(request, orderNo, orderedAt, amountSummary);
        List<Map<String, Object>> payloadItems = appendItemsAndBuildReservePayload(order, request.items());

        saveCreatedOrder(order, eventId);

        String sagaId = startSaga(order, eventId, orderedAt);
        publishInventoryReserveRequested(order, request, amountSummary.payAmount(), payloadItems, eventId);

        return OrderCreateResponse.builder()
                .orderId(order.getId())
                .orderNo(orderNo)
                .status(OrderStatus.PENDING_RESERVE.name())
                .payAmount(amountSummary.payAmount())
                .sagaId(sagaId)
                .outboxEventId(eventId)
                .orderedAt(orderedAt)
                .build();
    }

    @WriteTx
    public OrderCancelResponse cancelOrder(String orderNo, OrderCancelRequest request) {
        OrderEntity order = orderRepository.findDetailByOrderNo(orderNo)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
        OrderStatus currentStatus = order.getStatus();

        validateCancellableStatus(currentStatus);

        String cancelReasonCode = resolveCancelReason(request);
        LocalDateTime cancelledAt = LocalDateTime.now();
        String cancelEventId = OrderIdGenerator.newEventId();

        applyOrderCancellation(order, currentStatus, cancelReasonCode, cancelledAt, cancelEventId);
        publishInventoryReleaseIfRequired(order, currentStatus, cancelReasonCode);

        return OrderCancelResponse.builder()
                .orderNo(orderNo)
                .status(OrderStatus.CANCELLED.name())
                .reasonCode(cancelReasonCode)
                .cancelledAt(cancelledAt)
                .build();
    }

    private OrderAmountSummary calculateAmountSummary(OrderCreateRequest request) {
        BigDecimal itemAmount = calculateItemAmount(request.items());
        BigDecimal shippingFee = safe(request.shippingFee());
        BigDecimal discountAmount = safe(request.discountAmount());
        BigDecimal pointUsedAmount = safe(request.pointUsedAmount());
        BigDecimal payAmount = itemAmount.add(shippingFee).subtract(discountAmount).subtract(pointUsedAmount);

        if (payAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_AMOUNT);
        }
        return new OrderAmountSummary(itemAmount, shippingFee, discountAmount, pointUsedAmount, payAmount);
    }

    private OrderEntity createOrderEntity(
            OrderCreateRequest request,
            String orderNo,
            LocalDateTime orderedAt,
            OrderAmountSummary amountSummary
    ) {
        OrderInfo orderInfo = OrderInfo.of(
                request.paymentMethod(),
                request.recipientName(),
                request.recipientPhone(),
                request.zipCode(),
                request.address1(),
                request.address2(),
                request.deliveryRequest()
        );

        return OrderEntity.create(
                orderNo,
                request.buyerId(),
                OrderStatus.PENDING_RESERVE,
                amountSummary.payAmount(),
                "KRW",
                amountSummary.itemAmount(),
                amountSummary.shippingFee(),
                amountSummary.discountAmount(),
                amountSummary.pointUsedAmount(),
                orderInfo,
                orderedAt
        );
    }

    private List<Map<String, Object>> appendItemsAndBuildReservePayload(
            OrderEntity order,
            List<OrderCreateItemRequest> requestItems
    ) {
        List<Map<String, Object>> payloadItems = new ArrayList<>(requestItems.size());
        for (OrderCreateItemRequest requestItem : requestItems) {
            BigDecimal lineAmount = requestItem.unitPrice().multiply(BigDecimal.valueOf(requestItem.quantity()));
            order.addItem(
                    requestItem.productId(),
                    requestItem.optionId(),
                    requestItem.sellerId(),
                    requestItem.productName(),
                    requestItem.optionName(),
                    requestItem.unitPrice(),
                    requestItem.quantity(),
                    lineAmount
            );
            payloadItems.add(toReservePayloadItem(requestItem));
        }
        return payloadItems;
    }

    private Map<String, Object> toReservePayloadItem(OrderCreateItemRequest requestItem) {
        Map<String, Object> payloadItem = new LinkedHashMap<>();
        payloadItem.put("productId", requestItem.productId());
        payloadItem.put("optionId", requestItem.optionId());
        payloadItem.put("sellerId", requestItem.sellerId());
        payloadItem.put("quantity", requestItem.quantity());
        return payloadItem;
    }

    private void saveCreatedOrder(OrderEntity order, String eventId) {
        orderRepository.save(order);
        saveStatusHistory(order, null, OrderStatus.PENDING_RESERVE, eventId, REASON_ORDER_CREATED);
    }

    private String startSaga(OrderEntity order, String eventId, LocalDateTime orderedAt) {
        String sagaId = OrderIdGenerator.newSagaId();
        OrderSagaEntity saga = OrderSagaEntity.create(
                order,
                sagaId,
                OrderSagaState.WAITING_INVENTORY,
                eventId,
                EVENT_ORDER_CREATED,
                orderedAt.plusMinutes(paymentResultTimeoutMinutes)
        );
        orderSagaRepository.save(saga);
        return sagaId;
    }

    private void publishInventoryReserveRequested(
            OrderEntity order,
            OrderCreateRequest request,
            BigDecimal payAmount,
            List<Map<String, Object>> payloadItems,
            String eventId
    ) {
        Map<String, Object> payload = buildInventoryReservePayload(order, request, payAmount, payloadItems);
        publishDomainEvent(
                eventId,
                EVENT_INVENTORY_RESERVE_REQUESTED,
                order.getId(),
                inventoryReserveTopic,
                payload
        );
    }

    private String resolveCancelReason(OrderCancelRequest request) {
        if (request == null || isBlank(request.reasonCode())) {
            return DEFAULT_CANCEL_REASON;
        }
        return request.reasonCode();
    }

    private void validateCancellableStatus(OrderStatus currentStatus) {
        if (!CANCELLABLE_STATUSES.contains(currentStatus)) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE);
        }
    }

    private void applyOrderCancellation(
            OrderEntity order,
            OrderStatus currentStatus,
            String cancelReasonCode,
            LocalDateTime cancelledAt,
            String cancelEventId
    ) {
        order.updateStatus(OrderStatus.CANCELLED, cancelReasonCode, null, cancelledAt);
        order.markItemsCancelled();
        saveStatusHistory(order, currentStatus, OrderStatus.CANCELLED, cancelEventId, cancelReasonCode);
        transitionSaga(order.getId(), OrderSagaState.CANCELLED, cancelEventId, EVENT_ORDER_CANCELLED, cancelReasonCode);
    }

    private void publishInventoryReleaseIfRequired(
            OrderEntity order,
            OrderStatus currentStatus,
            String cancelReasonCode
    ) {
        if (!RELEASE_REQUIRED_STATUSES.contains(currentStatus)) {
            return;
        }

        String releaseEventId = OrderIdGenerator.newEventId();
        Map<String, Object> releasePayload = new LinkedHashMap<>();
        releasePayload.put("orderId", order.getId());
        releasePayload.put("orderNo", order.getOrderNo());
        releasePayload.put("reasonCode", cancelReasonCode);

        publishDomainEvent(
                releaseEventId,
                EVENT_INVENTORY_RELEASE_REQUESTED,
                order.getId(),
                inventoryCommandTopic,
                releasePayload
        );
    }

    private Map<String, Object> buildInventoryReservePayload(
            OrderEntity order,
            OrderCreateRequest request,
            BigDecimal payAmount,
            List<Map<String, Object>> payloadItems
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("buyerId", request.buyerId());
        payload.put("payAmount", payAmount);
        payload.put("paymentMethod", request.paymentMethod());
        payload.put("recipientName", request.recipientName());
        payload.put("recipientPhone", request.recipientPhone());
        payload.put("zipCode", request.zipCode());
        payload.put("address1", request.address1());
        payload.put("address2", request.address2());
        payload.put("items", payloadItems);
        return payload;
    }

    private void publishDomainEvent(
            String eventId,
            String eventType,
            Long orderId,
            String topic,
            Map<String, Object> payload
    ) {
        String aggregateId = String.valueOf(orderId);
        OrderDomainEvent event = OrderDomainEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .aggregateType(AGGREGATE_TYPE_ORDER)
                .aggregateId(aggregateId)
                .topic(topic)
                .partitionKey(aggregateId)
                .payload(payload)
                .build();
        orderDomainEventPublisher.publish(event);
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
            String eventType,
            String failReasonCode
    ) {
        orderSagaRepository.findByOrder_Id(orderId)
                .ifPresent(saga -> saga.transition(nextState, eventId, eventType, failReasonCode));
    }

    private void validateCreateRequest(OrderCreateRequest request) {
        if (request == null) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_AMOUNT);
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new ErrorException(OrderErrorCode.ORDER_ITEM_EMPTY);
        }

        for (OrderCreateItemRequest item : request.items()) {
            if (item.productId() == null
                    || item.sellerId() == null
                    || item.unitPrice() == null
                    || item.quantity() == null
                    || item.quantity() <= 0
                    || item.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new ErrorException(OrderErrorCode.INVALID_ORDER_AMOUNT);
            }
        }
    }

    private BigDecimal calculateItemAmount(List<OrderCreateItemRequest> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderCreateItemRequest item : items) {
            total = total.add(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }
        return total;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record OrderAmountSummary(
            BigDecimal itemAmount,
            BigDecimal shippingFee,
            BigDecimal discountAmount,
            BigDecimal pointUsedAmount,
            BigDecimal payAmount
    ) {
    }
}

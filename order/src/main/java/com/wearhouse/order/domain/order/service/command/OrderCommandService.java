package com.wearhouse.order.domain.order.service.command;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.order.domain.order.dto.request.OrderCancelRequest;
import com.wearhouse.order.domain.order.dto.request.OrderCreateRequest;
import com.wearhouse.order.domain.order.dto.request.OrderCreateRequest.OrderCreateItemRequest;
import com.wearhouse.order.domain.order.dto.response.OrderCancelResponse;
import com.wearhouse.order.domain.order.dto.response.OrderCreateResponse;
import com.wearhouse.order.domain.order.entity.OrderEntity;
import com.wearhouse.order.domain.order.entity.OrderInfo;
import com.wearhouse.order.domain.order.entity.OrderSagaEntity;
import com.wearhouse.order.domain.order.entity.OrderStatusHistoryEntity;
import com.wearhouse.order.domain.order.event.OrderDomainEvent;
import com.wearhouse.order.domain.order.event.OrderDomainEventPublisher;
import com.wearhouse.order.domain.order.exception.OrderErrorCode;
import com.wearhouse.order.domain.order.model.OrderSagaState;
import com.wearhouse.order.domain.order.model.OrderStatus;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCommandService {

    private static final Set<OrderStatus> CANCELLABLE_STATUSES = Set.of(
            OrderStatus.PENDING_RESERVE,
            OrderStatus.RESERVE_FAILED,
            OrderStatus.RESERVED,
            OrderStatus.PAYMENT_PENDING,
            OrderStatus.PAYMENT_FAILED
    );
    private static final Set<OrderStatus> RELEASE_REQUIRED_STATUSES = Set.of(
            OrderStatus.RESERVED,
            OrderStatus.PAYMENT_PENDING,
            OrderStatus.PAYMENT_FAILED
    );

    private final OrderRepository orderRepository;
    private final OrderSagaRepository orderSagaRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderDomainEventPublisher orderDomainEventPublisher;
    private final String inventoryReserveTopic;
    private final String inventoryCommandTopic;
    private final int paymentResultTimeoutMinutes;

    public OrderCommandService(
            OrderRepository orderRepository,
            OrderSagaRepository orderSagaRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            OrderDomainEventPublisher orderDomainEventPublisher,
            @Value("${wearhouse.kafka.inventory-reserve-topic:wearhouse.inventory.command.v1}") String inventoryReserveTopic,
            @Value("${wearhouse.kafka.inventory-command-topic:wearhouse.inventory.command.v1}") String inventoryCommandTopic,
            @Value("${wearhouse.order.payment-result-timeout-minutes:30}") int paymentResultTimeoutMinutes
    ) {
        this.orderRepository = orderRepository;
        this.orderSagaRepository = orderSagaRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.orderDomainEventPublisher = orderDomainEventPublisher;
        this.inventoryReserveTopic = inventoryReserveTopic;
        this.inventoryCommandTopic = inventoryCommandTopic;
        this.paymentResultTimeoutMinutes = paymentResultTimeoutMinutes;
    }

    @Transactional
    public OrderCreateResponse createOrder(OrderCreateRequest request) {
        validateCreateRequest(request);

        OrderAmountSummary amountSummary = calculateAmountSummary(request);
        String orderNo = OrderIdGenerator.newOrderNo();
        LocalDateTime orderedAt = LocalDateTime.now();
        String eventId = OrderIdGenerator.newEventId();

        OrderEntity order = createOrderEntity(request, orderNo, orderedAt, amountSummary);
        List<Map<String, Object>> payloadItems = appendItemsAndBuildPayload(order, request.items());

        orderRepository.save(order);
        persistOrderCreatedHistory(order, eventId);

        String sagaId = initializeSaga(order, eventId, orderedAt);
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

    @Transactional
    public OrderCancelResponse cancelOrder(String orderNo, OrderCancelRequest request) {
        OrderEntity order = orderRepository.findDetailByOrderNo(orderNo)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
        OrderStatus currentStatus = order.getStatus();

        if (!CANCELLABLE_STATUSES.contains(currentStatus)) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE);
        }

        String cancelReasonCode = resolveCancelReason(request);
        LocalDateTime cancelledAt = LocalDateTime.now();
        String cancelEventId = OrderIdGenerator.newEventId();

        updateOrderCancelState(order, currentStatus, cancelReasonCode, cancelledAt, cancelEventId);
        publishInventoryReleaseIfRequired(order, currentStatus, orderNo, cancelReasonCode);

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

    private List<Map<String, Object>> appendItemsAndBuildPayload(
            OrderEntity order,
            List<OrderCreateItemRequest> requestItems
    ) {
        List<Map<String, Object>> payloadItems = new ArrayList<>();
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
            payloadItems.add(toPayloadItem(requestItem));
        }
        return payloadItems;
    }

    private Map<String, Object> toPayloadItem(OrderCreateItemRequest requestItem) {
        Map<String, Object> payloadItem = new LinkedHashMap<>();
        payloadItem.put("productId", requestItem.productId());
        payloadItem.put("optionId", requestItem.optionId());
        payloadItem.put("sellerId", requestItem.sellerId());
        payloadItem.put("quantity", requestItem.quantity());
        return payloadItem;
    }

    private void persistOrderCreatedHistory(OrderEntity order, String eventId) {
        orderStatusHistoryRepository.save(OrderStatusHistoryEntity.create(
                order,
                null,
                OrderStatus.PENDING_RESERVE,
                eventId,
                "ORDER_CREATED"
        ));
    }

    private String initializeSaga(OrderEntity order, String eventId, LocalDateTime orderedAt) {
        String sagaId = OrderIdGenerator.newSagaId();
        OrderSagaEntity saga = OrderSagaEntity.create(
                order,
                sagaId,
                OrderSagaState.WAITING_INVENTORY,
                eventId,
                "OrderCreated",
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
                "InventoryReserveRequested",
                String.valueOf(order.getId()),
                inventoryReserveTopic,
                payload
        );
    }

    private String resolveCancelReason(OrderCancelRequest request) {
        if (request == null || isBlank(request.reasonCode())) {
            return "BUYER_CANCEL";
        }
        return request.reasonCode();
    }

    private void updateOrderCancelState(
            OrderEntity order,
            OrderStatus currentStatus,
            String cancelReasonCode,
            LocalDateTime cancelledAt,
            String cancelEventId
    ) {
        order.updateStatus(OrderStatus.CANCELLED, cancelReasonCode, null, cancelledAt);
        order.markItemsCancelled();

        orderStatusHistoryRepository.save(OrderStatusHistoryEntity.create(
                order,
                currentStatus,
                OrderStatus.CANCELLED,
                cancelEventId,
                cancelReasonCode
        ));

        orderSagaRepository.findByOrder_Id(order.getId())
                .ifPresent(saga -> saga.transition(
                        OrderSagaState.CANCELLED,
                        cancelEventId,
                        "OrderCancelled",
                        cancelReasonCode
                ));
    }

    private void publishInventoryReleaseIfRequired(
            OrderEntity order,
            OrderStatus currentStatus,
            String orderNo,
            String cancelReasonCode
    ) {
        if (!RELEASE_REQUIRED_STATUSES.contains(currentStatus)) {
            return;
        }

        String releaseEventId = OrderIdGenerator.newEventId();
        Map<String, Object> releasePayload = new LinkedHashMap<>();
        releasePayload.put("orderId", order.getId());
        releasePayload.put("orderNo", orderNo);
        releasePayload.put("reasonCode", cancelReasonCode);

        publishDomainEvent(
                releaseEventId,
                "InventoryReleaseRequested",
                String.valueOf(order.getId()),
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
            String aggregateId,
            String topic,
            Map<String, Object> payload
    ) {
        OrderDomainEvent event = OrderDomainEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .aggregateType("ORDER")
                .aggregateId(aggregateId)
                .topic(topic)
                .partitionKey(aggregateId)
                .payload(payload)
                .build();
        orderDomainEventPublisher.publish(event);
    }

    private void validateCreateRequest(OrderCreateRequest request) {
        if (request == null || request.buyerId() == null) {
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

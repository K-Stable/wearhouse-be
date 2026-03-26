package com.wearhouse.order.domain.service.command;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.order.domain.dto.request.OrderCancelRequest;
import com.wearhouse.order.domain.dto.request.OrderCreateRequest;
import com.wearhouse.order.domain.dto.request.OrderCreateRequest.OrderCreateItemRequest;
import com.wearhouse.order.domain.dto.request.OrderPaymentConfirmRequest;
import com.wearhouse.order.domain.dto.response.OrderCancelResponse;
import com.wearhouse.order.domain.dto.response.OrderCreateResponse;
import com.wearhouse.order.domain.dto.response.OrderPaymentConfirmResponse;
import com.wearhouse.order.domain.dto.response.OrderPaymentPrepareResponse;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.entity.OrderInfo;
import com.wearhouse.order.domain.entity.OrderSagaEntity;
import com.wearhouse.order.domain.entity.OrderStatusHistoryEntity;
import com.wearhouse.order.domain.event.OrderDomainEvent;
import com.wearhouse.order.domain.event.OrderDomainEventPublisher;
import com.wearhouse.order.domain.event.OrderEventType;
import com.wearhouse.order.domain.exception.OrderErrorCode;
import com.wearhouse.order.domain.model.OrderSagaState;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.domain.model.PaymentMethod;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.infra.jpa.repository.OrderSagaRepository;
import com.wearhouse.order.infra.jpa.repository.OrderStatusHistoryRepository;
import com.wearhouse.order.infra.payment.OrderPaymentClient;
import com.wearhouse.order.infra.payment.dto.PaymentConfirmInternalRequest;
import com.wearhouse.order.infra.payment.dto.PaymentConfirmInternalResponse;
import com.wearhouse.order.infra.payment.dto.PaymentPrepareInternalRequest;
import com.wearhouse.order.infra.payment.dto.PaymentPrepareInternalResponse;
import com.wearhouse.order.support.OrderIdGenerator;
import com.wearhouse.order.support.config.OrderKafkaTopicsProperties;
import com.wearhouse.order.support.config.OrderProperties;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.wearhouse.common.global.transactional.WriteTx;

@Service
@RequiredArgsConstructor
public class OrderCommandService {

    private static final String AGGREGATE_TYPE_ORDER = "ORDER";
    private static final String REASON_ORDER_CREATED = "ORDER_CREATED";
    private static final String DEFAULT_CANCEL_REASON = "BUYER_CANCEL";

    private final OrderRepository orderRepository;
    private final OrderSagaRepository orderSagaRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderDomainEventPublisher orderDomainEventPublisher;
    private final OrderPaymentClient orderPaymentClient;
    private final OrderKafkaTopicsProperties kafkaTopicsProperties;
    private final OrderProperties orderProperties;
    private final DeliveryCommandService deliveryCommandService;

    @WriteTx
    public OrderCreateResponse createOrder(OrderCreateRequest request) {
        // 주문 생성의 1차 트랜잭션: 주문/사가 생성 + 재고예약 이벤트 발행까지 처리하고 즉시 응답한다.
        OrderCreateContext context = prepareCreateContext(request);
        OrderEntity order = createOrderEntity(
                request,
                context.orderNo(),
                context.orderedAt(),
                context.amountSummary()
        );
        List<Map<String, Object>> payloadItems = appendItemsAndBuildReservePayload(order, request.items());

        saveCreatedOrder(order, context.eventId());
        startSaga(order, context.eventId());
        publishInventoryReserveRequested(order, request, context, payloadItems);

        return OrderCreateResponse.builder()
                .orderNo(context.orderNo())
                .customerId(context.customerId())
                .customerName(request.recipientName())
                .payAmount(context.amountSummary().payAmount())
                .build();
    }

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
        // CARD는 외부 confirm API를 호출하지 않고 현재 주문 상태만 폴링해 최종 상태를 반환한다.
        if (paymentMethod != PaymentMethod.STABLE) {
            OrderStatus status = waitForConfirmResult(order.getId(), "PENDING");
            String reasonCode = orderRepository.findDetailById(order.getId()).map(OrderEntity::getFailReasonCode).orElse(null);
            return new OrderPaymentConfirmResponse(order.getId(), orderNo, status.name(), reasonCode);
        }
        // STABLE은 paymentKey 기반으로 payment-service confirm API를 호출한다.
        validateStablePaymentConfirmRequest(request);

        ApiResponse<PaymentConfirmInternalResponse> confirmResponse = orderPaymentClient.confirmStablepayPayment(
                orderProperties.getInternal().getSharedSecret(),
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
            return new OrderPaymentPrepareResponse(
                    null,
                    null,
                    null,
                    null
            );
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

        return new OrderPaymentPrepareResponse(
                data.checkoutSessionId(),
                data.checkoutUrl(),
                data.appLaunchUrl(),
                data.checkoutExpiresAt()
        );
    }

    @WriteTx
    public OrderCancelResponse cancelOrder(String orderNo, OrderCancelRequest request) {
        OrderEntity order = orderRepository.findDetailByOrderNo(orderNo)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
        OrderStatus currentStatus = order.getStatus();

        validateNotInDelivery(order.getId());
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

    private OrderStatus waitForConfirmResult(Long orderId, String paymentStatus) {
        // payment-service가 아직 PENDING이면 현재 order 상태를 즉시 반환한다.
        if ("PENDING".equalsIgnoreCase(paymentStatus)) {
            return orderRepository.findDetailById(orderId)
                    .map(OrderEntity::getStatus)
                    .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
        }

        // 결제 이벤트 소비(saga) 결과가 DB 상태로 반영될 때까지 짧게 대기한다.
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
            current = orderRepository.findDetailById(order.getId())
                    .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
        }
        return orderRepository.findDetailById(order.getId())
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private void validateConfirmOrder(OrderEntity order, Long buyerId, OrderPaymentConfirmRequest request) {
        if (order.getBuyerId() == null || !order.getBuyerId().equals(buyerId)) {
            throw new ErrorException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getId().equals(request.orderId())) {
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
        // 주문 최초 상태를 이력과 함께 저장한다.
        orderRepository.save(order);
        saveStatusHistory(order, null, OrderStatus.PENDING_RESERVE, eventId, REASON_ORDER_CREATED);
    }

    private void startSaga(OrderEntity order, String eventId) {
        // saga는 재고예약 결과를 기다리는 상태에서 시작한다.
        String sagaId = OrderIdGenerator.newSagaId();
        OrderSagaEntity saga = OrderSagaEntity.create(
                order,
                sagaId,
                OrderSagaState.WAITING_INVENTORY,
                eventId
        );
        orderSagaRepository.save(saga);
    }

    private void publishInventoryReserveRequested(
            OrderEntity order,
            OrderCreateRequest request,
            OrderCreateContext context,
            List<Map<String, Object>> payloadItems
    ) {
        // 다음 단계(재고 서비스)가 처리할 커맨드 이벤트를 발행한다.
        Map<String, Object> payload = buildInventoryReservePayload(order, request, context, payloadItems);
        publishDomainEvent(
                context.eventId(),
                OrderEventType.INVENTORY_RESERVE_REQUESTED,
                order.getId(),
                kafkaTopicsProperties.getInventoryReserveTopic(),
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
        if (!OrderStatusPolicy.CANCELLABLE_STATUSES.contains(currentStatus)) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE);
        }
    }

    private void validateNotInDelivery(Long orderId) {
        if (deliveryCommandService.isCancelBlockedByDelivery(orderId)) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "배송이 시작된 주문은 취소할 수 없습니다.");
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
        transitionSaga(order.getId(), OrderSagaState.CANCELLED, cancelEventId, cancelReasonCode);
    }

    private void publishInventoryReleaseIfRequired(
            OrderEntity order,
            OrderStatus currentStatus,
            String cancelReasonCode
    ) {
        if (!OrderStatusPolicy.RELEASE_REQUIRED_STATUSES.contains(currentStatus)) {
            return;
        }

        // 재고를 이미 예약한 상태에서만 보상(재고 해제) 이벤트를 보낸다.
        String releaseEventId = OrderIdGenerator.newEventId();
        Map<String, Object> releasePayload = new LinkedHashMap<>();
        releasePayload.put("orderId", order.getId());
        releasePayload.put("orderNo", order.getOrderNo());
        releasePayload.put("reasonCode", cancelReasonCode);

        publishDomainEvent(
                releaseEventId,
                OrderEventType.INVENTORY_RELEASE_REQUESTED,
                order.getId(),
                kafkaTopicsProperties.getInventoryCommandTopic(),
                releasePayload
        );
    }

    private Map<String, Object> buildInventoryReservePayload(
            OrderEntity order,
            OrderCreateRequest request,
            OrderCreateContext context,
            List<Map<String, Object>> payloadItems
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("buyerId", request.buyerId());
        payload.put("payAmount", context.amountSummary().payAmount());
        payload.put("paymentMethod", request.paymentMethod() == null ? null : request.paymentMethod().name());
        payload.put("recipientName", request.recipientName());
        payload.put("recipientPhone", request.recipientPhone());
        payload.put("zipCode", request.zipCode());
        payload.put("address1", request.address1());
        payload.put("address2", request.address2());
        payload.put("orderedAt", context.orderedAt());
        payload.put("items", payloadItems);
        return payload;
    }

    private OrderCreateContext prepareCreateContext(OrderCreateRequest request) {
        validateCreateRequest(request);

        OrderAmountSummary amountSummary = calculateAmountSummary(request);
        String orderNo = OrderIdGenerator.newOrderNo();
        LocalDateTime orderedAt = LocalDateTime.now();
        String eventId = OrderIdGenerator.newEventId();
        String customerKey = resolveCustomerKey(request.buyerId(), orderNo);
        String customerId = UUID.nameUUIDFromBytes(customerKey.getBytes(StandardCharsets.UTF_8)).toString();

        return new OrderCreateContext(orderNo, orderedAt, eventId, customerId, amountSummary);
    }

    private void publishDomainEvent(
            String eventId,
            String eventType,
            Long orderId,
            String topic,
            Map<String, Object> payload
    ) {
        // 실제 Kafka 전송은 Outbox Listener(AFTER_COMMIT)에서 수행된다.
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
            String failReasonCode
    ) {
        orderSagaRepository.findByOrder_Id(orderId)
                .ifPresent(saga -> saga.transition(nextState, eventId, failReasonCode));
    }

    private void validateCreateRequest(OrderCreateRequest request) {
        if (request == null) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_AMOUNT);
        }
        if (request.paymentMethod() == null) {
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

    private static final class OrderStatusPolicy {
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

        private static final Set<OrderStatus> PAYMENT_CONFIRMABLE_STATUSES = Set.of(
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.PAYMENT_FAILED,
                OrderStatus.CONFIRMED
        );

        private static final Set<OrderStatus> PAYMENT_PREPARE_WAIT_STATUSES = Set.of(
                OrderStatus.PENDING_RESERVE,
                OrderStatus.RESERVED
        );

        private static final Set<OrderStatus> PAYMENT_PREPARABLE_STATUSES = Set.of(
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.PAYMENT_FAILED,
                OrderStatus.CONFIRMED
        );
    }

    private record OrderAmountSummary(
            BigDecimal itemAmount,
            BigDecimal shippingFee,
            BigDecimal discountAmount,
            BigDecimal pointUsedAmount,
            BigDecimal payAmount
    ) {
    }

    private record OrderCreateContext(
            String orderNo,
            LocalDateTime orderedAt,
            String eventId,
            String customerId,
            OrderAmountSummary amountSummary
    ) {
    }
}

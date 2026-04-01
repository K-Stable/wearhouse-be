package com.wearhouse.order.internal.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.order.common.exception.OrderErrorCode;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.entity.OrderInboxEventEntity;
import com.wearhouse.order.domain.entity.OrderOutboxEventEntity;
import com.wearhouse.order.domain.entity.OrderSagaEntity;
import com.wearhouse.order.domain.entity.OrderSagaHistoryEntity;
import com.wearhouse.order.domain.entity.OrderStatusHistoryEntity;
import com.wearhouse.order.infra.jpa.repository.OrderInboxRepository;
import com.wearhouse.order.infra.jpa.repository.OrderOutboxEventRepository;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.infra.jpa.repository.OrderSagaHistoryRepository;
import com.wearhouse.order.infra.jpa.repository.OrderSagaRepository;
import com.wearhouse.order.infra.jpa.repository.OrderStatusHistoryRepository;
import com.wearhouse.order.internal.dto.response.OrderFlowTrackingResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderFlowTrackingQueryService {

    private static final LocalDateTime MIN_TIME = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final OrderRepository orderRepository;
    private final OrderSagaRepository orderSagaRepository;
    private final OrderSagaHistoryRepository orderSagaHistoryRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderOutboxEventRepository orderOutboxEventRepository;
    private final OrderInboxRepository orderInboxRepository;

    @ReadTx
    public OrderFlowTrackingResponse getByOrderNo(String orderNo) {
        OrderEntity order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));

        Long orderId = order.getId();
        String partitionKey = String.valueOf(orderId);

        OrderSagaEntity saga = orderSagaRepository.findByOrder_Id(orderId).orElse(null);
        List<OrderStatusHistoryEntity> statusHistory = orderStatusHistoryRepository.findByOrder_IdOrderByIdAsc(orderId);
        List<OrderSagaHistoryEntity> sagaHistory = orderSagaHistoryRepository.findByOrder_IdOrderByIdAsc(orderId);
        List<OrderOutboxEventEntity> outboxEvents = orderOutboxEventRepository.findByPartitionKeyOrderByIdAsc(partitionKey);
        List<OrderInboxEventEntity> inboxEvents = orderInboxRepository.findByOrderId(orderId);

        List<OrderFlowTrackingResponse.StatusTransitionItem> statusItems = mapStatusHistory(statusHistory);
        List<OrderFlowTrackingResponse.SagaTransitionItem> sagaItems = mapSagaHistory(sagaHistory);
        List<OrderFlowTrackingResponse.KafkaOutboxItem> outboxItems = mapOutbox(outboxEvents);
        List<OrderFlowTrackingResponse.KafkaInboxItem> inboxItems = mapInbox(inboxEvents);
        List<OrderFlowTrackingResponse.TimelineItem> timeline = buildTimeline(statusItems, sagaItems, outboxItems, inboxItems);

        return new OrderFlowTrackingResponse(
                orderId,
                order.getOrderNo(),
                enumName(order.getStatus()),
                saga == null ? null : enumName(saga.getState()),
                saga == null ? null : saga.getLastEventId(),
                saga == null ? null : saga.getFailReasonCode(),
                statusItems,
                sagaItems,
                outboxItems,
                inboxItems,
                timeline
        );
    }

    private List<OrderFlowTrackingResponse.StatusTransitionItem> mapStatusHistory(List<OrderStatusHistoryEntity> entities) {
        List<OrderFlowTrackingResponse.StatusTransitionItem> items = new ArrayList<>(entities.size());
        for (OrderStatusHistoryEntity entity : entities) {
            items.add(new OrderFlowTrackingResponse.StatusTransitionItem(
                    entity.getId(),
                    entity.getEventId(),
                    enumName(entity.getFromStatus()),
                    enumName(entity.getToStatus()),
                    entity.getReasonCode(),
                    entity.getChangedAt()
            ));
        }
        return items;
    }

    private List<OrderFlowTrackingResponse.SagaTransitionItem> mapSagaHistory(List<OrderSagaHistoryEntity> entities) {
        List<OrderFlowTrackingResponse.SagaTransitionItem> items = new ArrayList<>(entities.size());
        for (OrderSagaHistoryEntity entity : entities) {
            items.add(new OrderFlowTrackingResponse.SagaTransitionItem(
                    entity.getId(),
                    entity.getEventId(),
                    enumName(entity.getFromState()),
                    enumName(entity.getToState()),
                    entity.getReasonCode(),
                    entity.getChangedAt()
            ));
        }
        return items;
    }

    private List<OrderFlowTrackingResponse.KafkaOutboxItem> mapOutbox(List<OrderOutboxEventEntity> entities) {
        List<OrderFlowTrackingResponse.KafkaOutboxItem> items = new ArrayList<>(entities.size());
        for (OrderOutboxEventEntity entity : entities) {
            items.add(new OrderFlowTrackingResponse.KafkaOutboxItem(
                    entity.getId(),
                    entity.getEventId(),
                    entity.getEventType(),
                    entity.getTopic(),
                    entity.getPartitionKey(),
                    enumName(entity.getStatus()),
                    entity.getFailMessage(),
                    entity.getCreatedAt(),
                    entity.getSentAt()
            ));
        }
        return items;
    }

    private List<OrderFlowTrackingResponse.KafkaInboxItem> mapInbox(List<OrderInboxEventEntity> entities) {
        List<OrderFlowTrackingResponse.KafkaInboxItem> items = new ArrayList<>(entities.size());
        for (OrderInboxEventEntity entity : entities) {
            items.add(new OrderFlowTrackingResponse.KafkaInboxItem(
                    entity.getId(),
                    entity.getEventId(),
                    entity.getEventType(),
                    entity.getTopic(),
                    entity.getPartitionKey(),
                    enumName(entity.getStatus()),
                    entity.getFailReasonCode(),
                    entity.getFailReasonMessage(),
                    entity.getCreatedAt(),
                    entity.getProcessedAt()
            ));
        }
        return items;
    }

    private List<OrderFlowTrackingResponse.TimelineItem> buildTimeline(
            List<OrderFlowTrackingResponse.StatusTransitionItem> statusItems,
            List<OrderFlowTrackingResponse.SagaTransitionItem> sagaItems,
            List<OrderFlowTrackingResponse.KafkaOutboxItem> outboxItems,
            List<OrderFlowTrackingResponse.KafkaInboxItem> inboxItems
    ) {
        List<OrderFlowTrackingResponse.TimelineItem> timeline = new ArrayList<>();

        for (OrderFlowTrackingResponse.StatusTransitionItem item : statusItems) {
            timeline.add(new OrderFlowTrackingResponse.TimelineItem(
                    item.changedAt(),
                    "ORDER_STATUS",
                    item.eventId(),
                    "OrderStatusChanged",
                    item.fromStatus(),
                    item.toStatus(),
                    null,
                    null,
                    item.reasonCode(),
                    "주문 상태 전이"
            ));
        }

        for (OrderFlowTrackingResponse.SagaTransitionItem item : sagaItems) {
            timeline.add(new OrderFlowTrackingResponse.TimelineItem(
                    item.changedAt(),
                    "ORDER_SAGA",
                    item.eventId(),
                    "OrderSagaTransition",
                    item.fromState(),
                    item.toState(),
                    null,
                    null,
                    item.reasonCode(),
                    "사가 상태 전이"
            ));
        }

        for (OrderFlowTrackingResponse.KafkaOutboxItem item : outboxItems) {
            timeline.add(new OrderFlowTrackingResponse.TimelineItem(
                    preferredTime(item.sentAt(), item.createdAt()),
                    "KAFKA_OUTBOX",
                    item.eventId(),
                    item.eventType(),
                    null,
                    null,
                    item.topic(),
                    item.status(),
                    null,
                    "Kafka 발행(outbox)"
            ));
        }

        for (OrderFlowTrackingResponse.KafkaInboxItem item : inboxItems) {
            timeline.add(new OrderFlowTrackingResponse.TimelineItem(
                    preferredTime(item.processedAt(), item.createdAt()),
                    "KAFKA_INBOX",
                    item.eventId(),
                    item.eventType(),
                    null,
                    null,
                    item.topic(),
                    item.status(),
                    item.failReasonCode(),
                    "Kafka 소비(inbox)"
            ));
        }

        timeline.sort(
                Comparator.comparing(
                                OrderFlowTrackingResponse.TimelineItem::occurredAt,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(item -> item.phase() == null ? "" : item.phase())
                        .thenComparing(item -> item.eventId() == null ? "" : item.eventId())
        );
        return timeline;
    }

    private LocalDateTime preferredTime(LocalDateTime primary, LocalDateTime fallback) {
        if (primary != null) {
            return primary;
        }
        if (fallback != null) {
            return fallback;
        }
        return MIN_TIME;
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}

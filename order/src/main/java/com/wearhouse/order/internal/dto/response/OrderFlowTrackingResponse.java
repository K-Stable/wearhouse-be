package com.wearhouse.order.internal.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record OrderFlowTrackingResponse(
        Long orderId,
        String orderNo,
        String currentOrderStatus,
        String currentSagaState,
        String lastSagaEventId,
        String sagaFailReasonCode,
        List<StatusTransitionItem> statusTransitions,
        List<SagaTransitionItem> sagaTransitions,
        List<KafkaOutboxItem> kafkaPublishedEvents,
        List<KafkaInboxItem> kafkaConsumedEvents,
        List<TimelineItem> timeline
) {

    public record StatusTransitionItem(
            Long id,
            String eventId,
            String fromStatus,
            String toStatus,
            String reasonCode,
            LocalDateTime changedAt
    ) {
    }

    public record SagaTransitionItem(
            Long id,
            String eventId,
            String fromState,
            String toState,
            String reasonCode,
            LocalDateTime changedAt
    ) {
    }

    public record KafkaOutboxItem(
            Long id,
            String eventId,
            String eventType,
            String topic,
            String partitionKey,
            String status,
            String failMessage,
            LocalDateTime createdAt,
            LocalDateTime sentAt
    ) {
    }

    public record KafkaInboxItem(
            Long id,
            String eventId,
            String eventType,
            String topic,
            String partitionKey,
            String status,
            String failReasonCode,
            String failReasonMessage,
            LocalDateTime createdAt,
            LocalDateTime processedAt
    ) {
    }

    public record TimelineItem(
            LocalDateTime occurredAt,
            String phase,
            String eventId,
            String eventType,
            String fromValue,
            String toValue,
            String topic,
            String status,
            String reasonCode,
            String note
    ) {
    }
}

package com.wearhouse.order.infra.jpa.repository;

import com.wearhouse.order.domain.entity.OrderOutboxEventEntity;
import com.wearhouse.order.domain.model.OrderOutboxStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderOutboxRepository {

    private final OrderOutboxEventRepository orderOutboxEventRepository;

    public void saveReady(
            String eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String partitionKey,
            String payload
    ) {
        OrderOutboxEventEntity entity = OrderOutboxEventEntity.ready(
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                topic,
                partitionKey,
                payload
        );
        orderOutboxEventRepository.save(entity);
    }

    public void markSuccess(String eventId) {
        orderOutboxEventRepository.findByEventId(eventId)
                .ifPresent(OrderOutboxEventEntity::markSuccess);
    }

    public void markFail(
            String eventId,
            int retryCount,
            LocalDateTime nextRetryAt,
            String errorCode,
            String errorMessage
    ) {
        orderOutboxEventRepository.findByEventId(eventId)
                .ifPresent(entity -> entity.markFailed(retryCount, nextRetryAt, errorCode, errorMessage));
    }

    public void markDead(String eventId, int retryCount, String errorCode, String errorMessage) {
        orderOutboxEventRepository.findByEventId(eventId)
                .ifPresent(entity -> entity.markDead(retryCount, errorCode, errorMessage));
    }

    public List<OutboxCandidate> lockRepublishCandidates(LocalDateTime cutoffAt, int limit) {
        List<OrderOutboxEventEntity> entities = orderOutboxEventRepository.lockRepublishCandidates(cutoffAt, limit);
        List<OutboxCandidate> candidates = new ArrayList<>();
        for (OrderOutboxEventEntity entity : entities) {
            if (entity.getStatus() != OrderOutboxStatus.SEND_SUCCESS) {
                candidates.add(new OutboxCandidate(
                        entity.getEventId(),
                        entity.getEventType(),
                        entity.getTopic(),
                        entity.getPartitionKey(),
                        entity.getPayload(),
                        entity.getRetryCount()
                ));
            }
        }
        return candidates;
    }

    public static class OutboxCandidate {
        private final String eventId;
        private final String eventType;
        private final String topic;
        private final String partitionKey;
        private final String payload;
        private final int retryCount;

        public OutboxCandidate(
                String eventId,
                String eventType,
                String topic,
                String partitionKey,
                String payload,
                int retryCount
        ) {
            this.eventId = eventId;
            this.eventType = eventType;
            this.topic = topic;
            this.partitionKey = partitionKey;
            this.payload = payload;
            this.retryCount = retryCount;
        }

        public String getEventId() {
            return eventId;
        }

        public String getTopic() {
            return topic;
        }

        public String getEventType() {
            return eventType;
        }

        public String getPartitionKey() {
            return partitionKey;
        }

        public String getPayload() {
            return payload;
        }

        public int getRetryCount() {
            return retryCount;
        }
    }
}

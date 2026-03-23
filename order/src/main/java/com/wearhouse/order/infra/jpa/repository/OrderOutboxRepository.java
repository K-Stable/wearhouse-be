package com.wearhouse.order.infra.jpa.repository;

import com.wearhouse.order.domain.entity.OrderOutboxEventEntity;
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
            String eventType,
            String topic,
            String partitionKey,
            String payload
    ) {
        OrderOutboxEventEntity entity = OrderOutboxEventEntity.ready(
                eventId,
                eventType,
                topic,
                partitionKey,
                payload
        );
        orderOutboxEventRepository.save(entity);
    }

    public void markSuccess(String eventId) {
        orderOutboxEventRepository.findByEventId(eventId)
                .ifPresent(entity -> {
                    entity.markSuccess();
                    orderOutboxEventRepository.save(entity);
                });
    }

    public void markFail(
            String eventId,
            String errorCode,
            String errorMessage
    ) {
        orderOutboxEventRepository.findByEventId(eventId)
                .ifPresent(entity -> {
                    entity.markFailed(errorMessage);
                    orderOutboxEventRepository.save(entity);
                });
    }

    public List<OutboxCandidate> lockRepublishCandidates(int limit) {
        List<OrderOutboxEventEntity> entities = orderOutboxEventRepository.lockRepublishCandidates(limit);
        List<OutboxCandidate> candidates = new ArrayList<>();
        for (OrderOutboxEventEntity entity : entities) {
            candidates.add(new OutboxCandidate(
                    entity.getEventId(),
                    entity.getEventType(),
                    entity.getTopic(),
                    entity.getPartitionKey(),
                    entity.getPayload()
            ));
        }
        return candidates;
    }

    public static class OutboxCandidate {
        private final String eventId;
        private final String eventType;
        private final String topic;
        private final String partitionKey;
        private final String payload;

        public OutboxCandidate(
                String eventId,
                String eventType,
                String topic,
                String partitionKey,
                String payload
        ) {
            this.eventId = eventId;
            this.eventType = eventType;
            this.topic = topic;
            this.partitionKey = partitionKey;
            this.payload = payload;
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
    }
}

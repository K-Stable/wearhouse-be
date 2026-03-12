package com.wearhouse.inventory.domain.repository;

import com.wearhouse.inventory.domain.entity.InventoryOutboxEventEntity;
import com.wearhouse.inventory.domain.model.InventoryOutboxStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InventoryOutboxRepository {

    private final InventoryOutboxEventRepository inventoryOutboxEventRepository;

    public void saveReady(
            String eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String partitionKey,
            String payload
    ) {
        InventoryOutboxEventEntity entity = InventoryOutboxEventEntity.ready(
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                topic,
                partitionKey,
                payload
        );
        inventoryOutboxEventRepository.save(entity);
    }

    public void markSuccess(String eventId) {
        inventoryOutboxEventRepository.findByEventId(eventId)
                .ifPresent(entity -> {
                    entity.markSuccess();
                    inventoryOutboxEventRepository.save(entity);
                });
    }

    public void markFail(
            String eventId,
            int retryCount,
            LocalDateTime nextRetryAt,
            String errorCode,
            String errorMessage
    ) {
        inventoryOutboxEventRepository.findByEventId(eventId)
                .ifPresent(entity -> {
                    entity.markFailed(retryCount, nextRetryAt, errorCode, errorMessage);
                    inventoryOutboxEventRepository.save(entity);
                });
    }

    public void markDead(String eventId, int retryCount, String errorCode, String errorMessage) {
        inventoryOutboxEventRepository.findByEventId(eventId)
                .ifPresent(entity -> {
                    entity.markDead(retryCount, errorCode, errorMessage);
                    inventoryOutboxEventRepository.save(entity);
                });
    }

    public List<OutboxCandidate> lockRepublishCandidates(LocalDateTime cutoffAt, int limit) {
        List<InventoryOutboxEventEntity> entities = inventoryOutboxEventRepository.lockRepublishCandidates(cutoffAt, limit);
        List<OutboxCandidate> candidates = new ArrayList<>();
        for (InventoryOutboxEventEntity entity : entities) {
            if (entity.getStatus() != InventoryOutboxStatus.SEND_SUCCESS) {
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

        public String getEventType() {
            return eventType;
        }

        public String getTopic() {
            return topic;
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

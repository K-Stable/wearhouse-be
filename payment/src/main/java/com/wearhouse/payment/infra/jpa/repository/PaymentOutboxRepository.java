package com.wearhouse.payment.infra.jpa.repository;

import com.wearhouse.payment.domain.payment.entity.PaymentOutboxEventEntity;
import com.wearhouse.payment.domain.payment.model.PaymentOutboxStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentOutboxRepository {

    private final PaymentOutboxEventJpaRepository paymentOutboxEventJpaRepository;

    public void saveReady(
            String eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String partitionKey,
            String payload
    ) {
        PaymentOutboxEventEntity entity = PaymentOutboxEventEntity.ready(
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                topic,
                partitionKey,
                payload
        );
        paymentOutboxEventJpaRepository.save(entity);
    }

    public void markSuccess(String eventId) {
        paymentOutboxEventJpaRepository.findByEventId(eventId)
                .ifPresent(entity -> {
                    entity.markSuccess();
                    paymentOutboxEventJpaRepository.save(entity);
                });
    }

    public void markFail(
            String eventId,
            String errorCode,
            String errorMessage
    ) {
        paymentOutboxEventJpaRepository.findByEventId(eventId)
                .ifPresent(entity -> {
                    entity.markFailed(errorCode, errorMessage);
                    paymentOutboxEventJpaRepository.save(entity);
                });
    }

    public List<OutboxCandidate> lockRepublishCandidates(LocalDateTime cutoffAt, int limit) {
        List<PaymentOutboxEventEntity> entities = paymentOutboxEventJpaRepository.lockRepublishCandidates(cutoffAt, limit);
        List<OutboxCandidate> candidates = new ArrayList<>();
        for (PaymentOutboxEventEntity entity : entities) {
            if (entity.getStatus() == PaymentOutboxStatus.FAIL) {
                candidates.add(new OutboxCandidate(
                        entity.getEventId(),
                        entity.getEventType(),
                        entity.getTopic(),
                        entity.getPartitionKey(),
                        entity.getPayload()
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
    }
}

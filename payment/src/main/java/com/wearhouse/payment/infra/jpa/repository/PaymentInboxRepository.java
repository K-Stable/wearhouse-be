package com.wearhouse.payment.infra.jpa.repository;

import com.wearhouse.payment.domain.payment.entity.PaymentInboxEventEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentInboxRepository {

    private final PaymentInboxEventJpaRepository paymentInboxEventJpaRepository;

    public boolean tryReceive(
            String eventId,
            String consumerName,
            String eventType,
            String topic,
            String partitionKey,
            String payload
    ) {
        try {
            paymentInboxEventJpaRepository.saveAndFlush(PaymentInboxEventEntity.received(
                    eventId,
                    consumerName,
                    eventType,
                    topic,
                    partitionKey,
                    payload
            ));
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    public void markProcessed(String eventId, String consumerName) {
        paymentInboxEventJpaRepository.findByEventIdAndConsumerName(eventId, consumerName)
                .ifPresent(entity -> {
                    entity.markProcessed();
                    paymentInboxEventJpaRepository.save(entity);
                });
    }

    public void markFailed(String eventId, String consumerName, String reasonCode, String reasonMessage) {
        paymentInboxEventJpaRepository.findByEventIdAndConsumerName(eventId, consumerName)
                .ifPresent(entity -> {
                    entity.markFailed(reasonCode, reasonMessage);
                    paymentInboxEventJpaRepository.save(entity);
                });
    }
}

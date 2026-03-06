package com.wearhouse.order.infra.jpa.repository;

import com.wearhouse.order.domain.order.entity.OrderInboxEventEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class OrderInboxRepository {

    private final OrderInboxEventJpaRepository orderInboxEventJpaRepository;

    public OrderInboxRepository(OrderInboxEventJpaRepository orderInboxEventJpaRepository) {
        this.orderInboxEventJpaRepository = orderInboxEventJpaRepository;
    }

    public boolean tryReceive(
            String eventId,
            String consumerName,
            String eventType,
            String topic,
            String partitionKey,
            String payload
    ) {
        OrderInboxEventEntity entity = OrderInboxEventEntity.received(
                eventId,
                consumerName,
                eventType,
                topic,
                partitionKey,
                payload
        );
        try {
            orderInboxEventJpaRepository.saveAndFlush(entity);
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    public void markProcessed(String eventId, String consumerName) {
        orderInboxEventJpaRepository.findByEventIdAndConsumerName(eventId, consumerName)
                .ifPresent(OrderInboxEventEntity::markProcessed);
    }

    public void markFailed(String eventId, String consumerName, String reasonCode, String reasonMessage) {
        orderInboxEventJpaRepository.findByEventIdAndConsumerName(eventId, consumerName)
                .ifPresent(entity -> entity.markFailed(reasonCode, reasonMessage));
    }
}

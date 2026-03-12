package com.wearhouse.order.infra.jpa.repository;

import com.wearhouse.order.domain.entity.OrderInboxEventEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class OrderInboxRepository {

    private final OrderInboxEventRepository orderInboxEventRepository;

    public OrderInboxRepository(OrderInboxEventRepository orderInboxEventRepository) {
        this.orderInboxEventRepository = orderInboxEventRepository;
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
            orderInboxEventRepository.saveAndFlush(entity);
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    public void markProcessed(String eventId, String consumerName) {
        orderInboxEventRepository.findByEventIdAndConsumerName(eventId, consumerName)
                .ifPresent(OrderInboxEventEntity::markProcessed);
    }

    public void markFailed(String eventId, String consumerName, String reasonCode, String reasonMessage) {
        orderInboxEventRepository.findByEventIdAndConsumerName(eventId, consumerName)
                .ifPresent(entity -> entity.markFailed(reasonCode, reasonMessage));
    }
}

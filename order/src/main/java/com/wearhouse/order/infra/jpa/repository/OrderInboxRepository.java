package com.wearhouse.order.infra.jpa.repository;

import com.wearhouse.order.domain.entity.OrderInboxEventEntity;
import org.springframework.stereotype.Repository;

@Repository
public class OrderInboxRepository {

    private final OrderInboxEventRepository orderInboxEventRepository;

    public OrderInboxRepository(OrderInboxEventRepository orderInboxEventRepository) {
        this.orderInboxEventRepository = orderInboxEventRepository;
    }

    public boolean tryReceive(
            String eventId,
            String consumerName
    ) {
        return orderInboxEventRepository.insertIgnoreReceived(eventId, consumerName) > 0;
    }

    public void markProcessed(String eventId, String consumerName) {
        orderInboxEventRepository.findByEventIdAndConsumerName(eventId, consumerName)
                .ifPresent(OrderInboxEventEntity::markProcessed);
    }

    public void markFailed(String eventId, String consumerName) {
        orderInboxEventRepository.findByEventIdAndConsumerName(eventId, consumerName)
                .ifPresent(OrderInboxEventEntity::markFailed);
    }
}

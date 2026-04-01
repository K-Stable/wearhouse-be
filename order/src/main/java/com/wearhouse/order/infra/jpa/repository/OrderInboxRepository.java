package com.wearhouse.order.infra.jpa.repository;

import com.wearhouse.order.domain.entity.OrderInboxEventEntity;
import java.util.List;
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
        return tryReceive(eventId, consumerName, null, null, null, null, null, null);
    }

    public boolean tryReceive(
            String eventId,
            String consumerName,
            String eventType,
            String topic,
            String partitionKey,
            String payload,
            Long orderId,
            String orderNo
    ) {
        return orderInboxEventRepository.insertIgnoreReceived(
                eventId,
                consumerName,
                eventType,
                topic,
                partitionKey,
                payload,
                orderId,
                orderNo
        ) > 0;
    }

    public void markProcessed(String eventId, String consumerName) {
        orderInboxEventRepository.findByEventIdAndConsumerName(eventId, consumerName)
                .ifPresent(OrderInboxEventEntity::markProcessed);
    }

    public void markFailed(String eventId, String consumerName) {
        markFailed(eventId, consumerName, null, null);
    }

    public void markFailed(
            String eventId,
            String consumerName,
            String reasonCode,
            String reasonMessage
    ) {
        orderInboxEventRepository.findByEventIdAndConsumerName(eventId, consumerName)
                .ifPresent(entity -> entity.markFailed(reasonCode, reasonMessage));
    }

    public List<OrderInboxEventEntity> findByOrderId(Long orderId) {
        return orderInboxEventRepository.findByOrderIdOrderByIdAsc(orderId);
    }
}

package com.wearhouse.order.infra.jpa.repository;

import com.wearhouse.order.domain.entity.OrderInboxEventEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderInboxEventRepository extends JpaRepository<OrderInboxEventEntity, Long> {

    @Modifying
    @Query(
            value = """
                    INSERT IGNORE INTO order_inbox_event
                    (event_id, consumer_name, event_type, topic, partition_key, payload, order_id, order_no, status, created_at, updated_at)
                    VALUES (:eventId, :consumerName, :eventType, :topic, :partitionKey, :payload, :orderId, :orderNo, 'RECEIVED', NOW(), NOW())
                    """,
            nativeQuery = true
    )
    int insertIgnoreReceived(
            @Param("eventId") String eventId,
            @Param("consumerName") String consumerName,
            @Param("eventType") String eventType,
            @Param("topic") String topic,
            @Param("partitionKey") String partitionKey,
            @Param("payload") String payload,
            @Param("orderId") Long orderId,
            @Param("orderNo") String orderNo
    );

    Optional<OrderInboxEventEntity> findByEventIdAndConsumerName(String eventId, String consumerName);

    List<OrderInboxEventEntity> findByOrderIdOrderByIdAsc(Long orderId);
}

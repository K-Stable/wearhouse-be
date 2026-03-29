package com.wearhouse.order.infra.jpa.repository;

import com.wearhouse.order.domain.entity.OrderInboxEventEntity;
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
                    (event_id, consumer_name, status, created_at, updated_at)
                    VALUES (:eventId, :consumerName, 'RECEIVED', NOW(), NOW())
                    """,
            nativeQuery = true
    )
    int insertIgnoreReceived(
            @Param("eventId") String eventId,
            @Param("consumerName") String consumerName
    );

    Optional<OrderInboxEventEntity> findByEventIdAndConsumerName(String eventId, String consumerName);
}

package com.wearhouse.order.domain.order.repository;

import com.wearhouse.order.domain.order.entity.OrderOutboxEventEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderOutboxEventJpaRepository extends JpaRepository<OrderOutboxEventEntity, Long> {

    Optional<OrderOutboxEventEntity> findByEventId(String eventId);

    @Query(value = """
            SELECT *
            FROM order_outbox_event
            WHERE status IN ('READY', 'SEND_FAIL')
              AND created_at <= :cutoffAt
              AND (next_retry_at IS NULL OR next_retry_at <= NOW())
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OrderOutboxEventEntity> lockRepublishCandidates(
            @Param("cutoffAt") LocalDateTime cutoffAt,
            @Param("limit") int limit
    );
}

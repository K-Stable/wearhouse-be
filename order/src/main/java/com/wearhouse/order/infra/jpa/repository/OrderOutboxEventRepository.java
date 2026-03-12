package com.wearhouse.order.infra.jpa.repository;

import com.wearhouse.order.domain.entity.OrderOutboxEventEntity;
import com.wearhouse.order.domain.model.OrderOutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderOutboxEventRepository extends JpaRepository<OrderOutboxEventEntity, Long> {

    Optional<OrderOutboxEventEntity> findByEventId(String eventId);
    long countByStatus(OrderOutboxStatus status);

    @Query(value = """
            SELECT *
            FROM order_outbox_event
            WHERE status = 'FAIL'
              AND created_at <= :cutoffAt
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OrderOutboxEventEntity> lockRepublishCandidates(
            @Param("cutoffAt") LocalDateTime cutoffAt,
            @Param("limit") int limit
    );
}

package com.wearhouse.inventory.domain.repository;

import com.wearhouse.inventory.domain.entity.InventoryOutboxEventEntity;
import com.wearhouse.inventory.domain.model.InventoryOutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryOutboxEventRepository extends JpaRepository<InventoryOutboxEventEntity, Long> {

    Optional<InventoryOutboxEventEntity> findByEventId(String eventId);

    long countByStatus(InventoryOutboxStatus status);

    @Query(value = """
            SELECT *
            FROM inventory_outbox_event
            WHERE status IN ('READY', 'SEND_FAIL')
              AND created_at <= :cutoffAt
              AND (next_retry_at IS NULL OR next_retry_at <= NOW())
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<InventoryOutboxEventEntity> lockRepublishCandidates(
            @Param("cutoffAt") LocalDateTime cutoffAt,
            @Param("limit") int limit
    );
}


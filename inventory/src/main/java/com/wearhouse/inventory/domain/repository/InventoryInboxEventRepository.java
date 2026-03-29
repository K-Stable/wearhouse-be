package com.wearhouse.inventory.domain.repository;

import com.wearhouse.inventory.domain.entity.InventoryInboxEventEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryInboxEventRepository extends JpaRepository<InventoryInboxEventEntity, Long> {

    @Modifying
    @Query(
            value = """
                    INSERT IGNORE INTO inventory_inbox_event
                    (event_id, consumer_name, status, created_at, updated_at)
                    VALUES (:eventId, :consumerName, 'RECEIVED', NOW(), NOW())
                    """,
            nativeQuery = true
    )
    int insertIgnoreReceived(
            @Param("eventId") String eventId,
            @Param("consumerName") String consumerName
    );

    boolean existsByEventIdAndConsumerName(String eventId, String consumerName);

    Optional<InventoryInboxEventEntity> findByEventIdAndConsumerName(String eventId, String consumerName);
}

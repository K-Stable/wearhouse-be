package com.wearhouse.inventory.domain.repository;

import com.wearhouse.inventory.domain.entity.InventoryInboxEventEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryInboxEventRepository extends JpaRepository<InventoryInboxEventEntity, Long> {

    boolean existsByEventIdAndConsumerName(String eventId, String consumerName);

    Optional<InventoryInboxEventEntity> findByEventIdAndConsumerName(String eventId, String consumerName);
}

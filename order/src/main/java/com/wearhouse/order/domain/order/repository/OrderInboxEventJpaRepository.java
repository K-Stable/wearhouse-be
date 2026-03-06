package com.wearhouse.order.domain.order.repository;

import com.wearhouse.order.domain.order.entity.OrderInboxEventEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderInboxEventJpaRepository extends JpaRepository<OrderInboxEventEntity, Long> {

    Optional<OrderInboxEventEntity> findByEventIdAndConsumerName(String eventId, String consumerName);
}

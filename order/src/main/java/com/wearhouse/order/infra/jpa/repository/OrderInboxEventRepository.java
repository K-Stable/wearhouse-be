package com.wearhouse.order.infra.jpa.repository;

import com.wearhouse.order.domain.entity.OrderInboxEventEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderInboxEventRepository extends JpaRepository<OrderInboxEventEntity, Long> {

    Optional<OrderInboxEventEntity> findByEventIdAndConsumerName(String eventId, String consumerName);
}

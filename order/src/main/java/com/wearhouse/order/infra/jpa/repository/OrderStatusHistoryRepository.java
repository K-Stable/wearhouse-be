package com.wearhouse.order.infra.jpa.repository;

import com.wearhouse.order.domain.entity.OrderStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, Long> {
}

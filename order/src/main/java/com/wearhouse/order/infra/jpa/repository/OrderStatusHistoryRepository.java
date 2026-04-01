package com.wearhouse.order.infra.jpa.repository;

import com.wearhouse.order.domain.entity.OrderStatusHistoryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, Long> {

    List<OrderStatusHistoryEntity> findByOrder_IdOrderByIdAsc(Long orderId);
}

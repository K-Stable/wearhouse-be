package com.wearhouse.order.infra.jpa.repository;

import com.wearhouse.order.domain.entity.OrderSagaHistoryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSagaHistoryRepository extends JpaRepository<OrderSagaHistoryEntity, Long> {

    List<OrderSagaHistoryEntity> findByOrder_IdOrderByIdAsc(Long orderId);
}

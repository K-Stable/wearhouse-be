package com.wearhouse.order.domain.order.repository;

import com.wearhouse.order.domain.order.entity.OrderSagaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSagaRepository extends JpaRepository<OrderSagaEntity, Long> {

    Optional<OrderSagaEntity> findByOrder_Id(Long orderId);
}

package com.wearhouse.order.infra.jpa.repository;

import com.wearhouse.order.domain.entity.OrderSagaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSagaRepository extends JpaRepository<OrderSagaEntity, Long> {

    Optional<OrderSagaEntity> findByOrder_Id(Long orderId);
}

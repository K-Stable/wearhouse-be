package com.wearhouse.order.infra.jpa.repository;

import com.wearhouse.order.domain.entity.OrderEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    Optional<OrderEntity> findByOrderNo(String orderNo);

    @EntityGraph(attributePaths = "items")
    @Query("select o from OrderEntity o where o.orderNo = :orderNo")
    Optional<OrderEntity> findDetailByOrderNo(@Param("orderNo") String orderNo);

    @EntityGraph(attributePaths = "items")
    @Query("select o from OrderEntity o where o.id = :orderId")
    Optional<OrderEntity> findDetailById(@Param("orderId") Long orderId);

    List<OrderEntity> findByBuyerIdOrderByIdDesc(Long buyerId, Pageable pageable);
}

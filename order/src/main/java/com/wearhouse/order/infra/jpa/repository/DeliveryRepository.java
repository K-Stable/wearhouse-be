package com.wearhouse.order.infra.jpa.repository;

import com.wearhouse.order.domain.entity.DeliveryEntity;
import com.wearhouse.order.domain.model.DeliveryStatus;
import com.wearhouse.order.domain.model.OrderStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryRepository extends JpaRepository<DeliveryEntity, Long> {

    List<DeliveryEntity> findByOrder_IdIn(Collection<Long> orderIds);

    boolean existsByOrder_IdAndStatusIn(Long orderId, Collection<DeliveryStatus> statuses);

    @Query("""
            select d
            from DeliveryEntity d
            join fetch d.order o
            where d.status = :deliveryStatus
              and d.updatedAt <= :threshold
              and o.status = :orderStatus
            order by d.id asc
            """)
    List<DeliveryEntity> findAutoConfirmTargets(
            @Param("deliveryStatus") DeliveryStatus deliveryStatus,
            @Param("threshold") LocalDateTime threshold,
            @Param("orderStatus") OrderStatus orderStatus,
            Pageable pageable
    );
}

package com.wearhouse.order.infra.jpa.repository;

import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.model.OrderStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
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

    @EntityGraph(attributePaths = "items")
    @Query("select distinct o from OrderEntity o where o.id in :orderIds")
    List<OrderEntity> findDetailsByIdIn(@Param("orderIds") Collection<Long> orderIds);

    @EntityGraph(attributePaths = "items")
    @Query(
            value = """
                    select o
                    from OrderEntity o
                    where (
                        :keyword is null
                        or lower(o.orderNo) like lower(concat('%', :keyword, '%'))
                        or exists (
                            select 1
                            from OrderItemEntity oi
                            where oi.order = o
                              and lower(oi.productNameSnapshot) like lower(concat('%', :keyword, '%'))
                        )
                    )
                    and (:status is null or o.status = :status)
                    order by o.id desc
                    """,
            countQuery = """
                    select count(o)
                    from OrderEntity o
                    where (
                        :keyword is null
                        or lower(o.orderNo) like lower(concat('%', :keyword, '%'))
                        or exists (
                            select 1
                            from OrderItemEntity oi
                            where oi.order = o
                              and lower(oi.productNameSnapshot) like lower(concat('%', :keyword, '%'))
                        )
                    )
                    and (:status is null or o.status = :status)
                    """
    )
    Page<OrderEntity> findOrdersForSellerDashboard(
            @Param("keyword") String keyword,
            @Param("status") OrderStatus status,
            Pageable pageable
    );
}

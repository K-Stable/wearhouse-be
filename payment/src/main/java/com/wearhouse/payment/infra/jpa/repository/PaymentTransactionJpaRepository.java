package com.wearhouse.payment.infra.jpa.repository;

import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.domain.payment.model.PaymentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentTransactionJpaRepository extends JpaRepository<PaymentTransactionEntity, Long> {

    Optional<PaymentTransactionEntity> findByOrderId(Long orderId);

    List<PaymentTransactionEntity> findByStatusAndExpiresAtIsNotNullAndExpiresAtLessThanEqualOrderByIdAsc(
            PaymentStatus status,
            LocalDateTime now,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PaymentTransactionEntity p
            SET p.status = com.wearhouse.payment.domain.payment.model.PaymentStatus.FAILED,
                p.reasonCode = :reasonCode,
                p.failedAt = :failedAt
            WHERE p.orderId = :orderId
              AND p.status = com.wearhouse.payment.domain.payment.model.PaymentStatus.PENDING
            """)
    int markFailedIfPending(
            @Param("orderId") Long orderId,
            @Param("reasonCode") String reasonCode,
            @Param("failedAt") LocalDateTime failedAt
    );
}


package com.wearhouse.payment.infra.jpa.repository;

import com.wearhouse.payment.domain.payment.entity.PaymentOutboxEventEntity;
import com.wearhouse.payment.domain.payment.model.PaymentOutboxStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentOutboxEventJpaRepository extends JpaRepository<PaymentOutboxEventEntity, Long> {

    Optional<PaymentOutboxEventEntity> findByEventId(String eventId);

    long countByStatus(PaymentOutboxStatus status);

    @Query(value = """
            SELECT *
            FROM payment_outbox_event
            WHERE status IN ('READY', 'FAIL')
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PaymentOutboxEventEntity> lockRepublishCandidates(@Param("limit") int limit);
}

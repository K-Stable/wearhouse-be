package com.wearhouse.payment.infra.jpa.repository;

import com.wearhouse.payment.domain.payment.entity.PaymentWebhookEventEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookJpaRepository extends JpaRepository<PaymentWebhookEventEntity, Long> {

    Optional<PaymentWebhookEventEntity> findByEventId(String eventId);
}

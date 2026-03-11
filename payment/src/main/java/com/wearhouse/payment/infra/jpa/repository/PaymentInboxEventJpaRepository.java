package com.wearhouse.payment.infra.jpa.repository;

import com.wearhouse.payment.domain.payment.entity.PaymentInboxEventEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentInboxEventJpaRepository extends JpaRepository<PaymentInboxEventEntity, Long> {

    Optional<PaymentInboxEventEntity> findByEventIdAndConsumerName(String eventId, String consumerName);
}


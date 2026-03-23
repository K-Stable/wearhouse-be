package com.wearhouse.payment.infra.jpa.repository;

import com.wearhouse.payment.domain.payment.entity.PaymentWebhookEventEntity;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentWebhookRepository {

    private final PaymentWebhookJpaRepository paymentWebhookJpaRepository;

    public Optional<PaymentWebhookEventEntity> findByEventId(String eventId) {
        return paymentWebhookJpaRepository.findByEventId(eventId);
    }

    public void save(PaymentWebhookEventEntity entity) {
        try {
            paymentWebhookJpaRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateKeyException("payment webhook event 중복 키 충돌", exception);
        }
    }
}

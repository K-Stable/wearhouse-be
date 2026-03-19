package com.wearhouse.payment.infra.jpa.repository;

import com.wearhouse.payment.domain.payment.entity.PaymentWebhookEventEntity;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentWebhookEventRepository {

    private final PaymentWebhookEventJpaRepository paymentWebhookEventJpaRepository;

    public Optional<PaymentWebhookEventEntity> findByEventId(String eventId) {
        return paymentWebhookEventJpaRepository.findByEventId(eventId);
    }

    public void save(PaymentWebhookEventEntity entity) {
        try {
            paymentWebhookEventJpaRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateKeyException("payment webhook event 중복 키 충돌", exception);
        }
    }
}

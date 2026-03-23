package com.wearhouse.payment.infra.jpa.repository;

import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.domain.payment.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentTransactionRepository {

    private final PaymentTransactionJpaRepository paymentTransactionJpaRepository;

    public Optional<PaymentTransactionEntity> findByOrderId(Long orderId) {
        return paymentTransactionJpaRepository.findByOrderId(orderId);
    }

    public Optional<PaymentTransactionEntity> findByPaymentId(String paymentId) {
        return paymentTransactionJpaRepository.findByPaymentId(paymentId);
    }

    public Optional<PaymentTransactionEntity> findByPaymentKey(String paymentKey) {
        return paymentTransactionJpaRepository.findByPaymentKey(paymentKey);
    }

    public PaymentTransactionEntity save(PaymentTransactionEntity entity) {
        return paymentTransactionJpaRepository.saveAndFlush(entity);
    }

    public void insertPending(
            String paymentId,
            Long orderId,
            String orderNo,
            BigDecimal amount,
            String paymentMethod,
            LocalDateTime expiresAt
    ) {
        saveWithDuplicateKeyTranslate(PaymentTransactionEntity.pending(
                paymentId,
                orderId,
                orderNo,
                amount,
                paymentMethod,
                expiresAt
        ));
    }

    public void insertAuthorized(
            String paymentId,
            Long orderId,
            String orderNo,
            BigDecimal amount,
            String paymentMethod,
            LocalDateTime authorizedAt
    ) {
        saveWithDuplicateKeyTranslate(PaymentTransactionEntity.authorized(
                paymentId,
                orderId,
                orderNo,
                amount,
                paymentMethod,
                authorizedAt
        ));
    }

    public void insertFailed(
            String paymentId,
            Long orderId,
            String orderNo,
            BigDecimal amount,
            String paymentMethod,
            String reasonCode,
            LocalDateTime failedAt
    ) {
        saveWithDuplicateKeyTranslate(PaymentTransactionEntity.failed(
                paymentId,
                orderId,
                orderNo,
                amount,
                paymentMethod,
                reasonCode,
                failedAt
        ));
    }

    public int markFailedIfPending(Long orderId, String reasonCode, LocalDateTime failedAt) {
        return paymentTransactionJpaRepository.markFailedIfPending(orderId, reasonCode, failedAt);
    }

    public List<PaymentTransactionEntity> findTimeoutCandidates(LocalDateTime now, int limit) {
        return paymentTransactionJpaRepository.findByStatusAndExpiresAtIsNotNullAndExpiresAtLessThanEqualOrderByIdAsc(
                PaymentStatus.PENDING,
                now,
                PageRequest.of(0, limit)
        );
    }

    private void saveWithDuplicateKeyTranslate(PaymentTransactionEntity entity) {
        try {
            paymentTransactionJpaRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateKeyException("payment transaction 중복 키 충돌", exception);
        }
    }
}

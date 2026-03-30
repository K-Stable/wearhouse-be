package com.wearhouse.payment.transaction.service;

import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.infra.jpa.repository.PaymentTransactionRepository;
import com.wearhouse.payment.support.PaymentIdGenerator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentTransactionCreateService {

    private static final String STABLE_METHOD = "STABLE";

    private final PaymentTransactionRepository paymentTransactionRepository;

    public Optional<PaymentTransactionEntity> findByOrderId(Long orderId) {
        return paymentTransactionRepository.findByOrderId(orderId);
    }

    public PaymentTransactionEntity getOrCreateStablePending(
            Long orderId,
            String orderNo,
            BigDecimal amount,
            int pendingTimeoutMinutes
    ) {
        Optional<PaymentTransactionEntity> existing = paymentTransactionRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            paymentTransactionRepository.insertPending(
                    PaymentIdGenerator.newPaymentId(),
                    orderId,
                    orderNo,
                    amount,
                    STABLE_METHOD,
                    LocalDateTime.now().plusMinutes(pendingTimeoutMinutes)
            );
        } catch (DuplicateKeyException ignored) {
            // 동시 요청에서 중복 생성된 경우, 아래 재조회로 진행한다.
        }

        return paymentTransactionRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주문의 결제 정보를 찾을 수 없습니다."));
    }

    public void insertPending(
            String paymentId,
            Long orderId,
            String orderNo,
            BigDecimal amount,
            String paymentMethod,
            LocalDateTime expiresAt
    ) {
        paymentTransactionRepository.insertPending(paymentId, orderId, orderNo, amount, paymentMethod, expiresAt);
    }

    public void insertAuthorized(
            String paymentId,
            Long orderId,
            String orderNo,
            BigDecimal amount,
            String paymentMethod,
            LocalDateTime authorizedAt
    ) {
        paymentTransactionRepository.insertAuthorized(paymentId, orderId, orderNo, amount, paymentMethod, authorizedAt);
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
        paymentTransactionRepository.insertFailed(paymentId, orderId, orderNo, amount, paymentMethod, reasonCode, failedAt);
    }
}


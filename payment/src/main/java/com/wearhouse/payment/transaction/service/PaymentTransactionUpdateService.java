package com.wearhouse.payment.transaction.service;

import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.infra.jpa.repository.PaymentTransactionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentTransactionUpdateService {

    private final PaymentTransactionRepository paymentTransactionRepository;

    public PaymentTransactionEntity save(PaymentTransactionEntity entity) {
        return paymentTransactionRepository.save(entity);
    }

    public int markFailedIfPending(Long orderId, String reasonCode, LocalDateTime failedAt) {
        return paymentTransactionRepository.markFailedIfPending(orderId, reasonCode, failedAt);
    }
}


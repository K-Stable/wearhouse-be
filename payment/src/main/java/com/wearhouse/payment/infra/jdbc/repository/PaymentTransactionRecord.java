package com.wearhouse.payment.infra.jdbc.repository;

import com.wearhouse.payment.domain.payment.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentTransactionRecord(
        Long id,
        String paymentId,
        Long orderId,
        String orderNo,
        BigDecimal amount,
        String paymentMethod,
        PaymentStatus status,
        String reasonCode,
        LocalDateTime expiresAt,
        LocalDateTime authorizedAt,
        LocalDateTime failedAt
) {
}

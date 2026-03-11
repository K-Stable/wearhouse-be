package com.wearhouse.payment.domain.payment.entity;

import com.wearhouse.payment.domain.payment.model.PaymentStatus;
import com.wearhouse.payment.infra.jpa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "payment_transaction")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransactionEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, length = 40, unique = true)
    private String paymentId;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "order_no", length = 40)
    private String orderNo;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "authorized_at")
    private LocalDateTime authorizedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Builder
    private PaymentTransactionEntity(
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
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.reasonCode = reasonCode;
        this.expiresAt = expiresAt;
        this.authorizedAt = authorizedAt;
        this.failedAt = failedAt;
    }

    public static PaymentTransactionEntity pending(
            String paymentId,
            Long orderId,
            String orderNo,
            BigDecimal amount,
            String paymentMethod,
            LocalDateTime expiresAt
    ) {
        return PaymentTransactionEntity.builder()
                .paymentId(paymentId)
                .orderId(orderId)
                .orderNo(orderNo)
                .amount(amount)
                .paymentMethod(paymentMethod)
                .status(PaymentStatus.PENDING)
                .expiresAt(expiresAt)
                .build();
    }

    public static PaymentTransactionEntity authorized(
            String paymentId,
            Long orderId,
            String orderNo,
            BigDecimal amount,
            String paymentMethod,
            LocalDateTime authorizedAt
    ) {
        return PaymentTransactionEntity.builder()
                .paymentId(paymentId)
                .orderId(orderId)
                .orderNo(orderNo)
                .amount(amount)
                .paymentMethod(paymentMethod)
                .status(PaymentStatus.AUTHORIZED)
                .authorizedAt(authorizedAt)
                .build();
    }

    public static PaymentTransactionEntity failed(
            String paymentId,
            Long orderId,
            String orderNo,
            BigDecimal amount,
            String paymentMethod,
            String reasonCode,
            LocalDateTime failedAt
    ) {
        return PaymentTransactionEntity.builder()
                .paymentId(paymentId)
                .orderId(orderId)
                .orderNo(orderNo)
                .amount(amount)
                .paymentMethod(paymentMethod)
                .status(PaymentStatus.FAILED)
                .reasonCode(reasonCode)
                .failedAt(failedAt)
                .build();
    }
}


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

    @Column(name = "payment_key", length = 80, unique = true)
    private String paymentKey;

    @Column(name = "payment_session_id", length = 80)
    private String paymentSessionId;

    @Column(name = "merchant_key", length = 120)
    private String merchantKey;

    @Column(name = "nonce", length = 120)
    private String nonce;

    @Column(name = "deadline", length = 120)
    private String deadline;

    @Column(name = "payload_hash", length = 128)
    private String payloadHash;

    @Column(name = "payer_address", length = 100)
    private String payerAddress;

    @Column(name = "token_address", length = 100)
    private String tokenAddress;

    @Column(name = "command_id", length = 80)
    private String commandId;

    @Column(name = "command_status", length = 40)
    private String commandStatus;

    @Column(name = "tx_hash", length = 120)
    private String txHash;

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
            String paymentKey,
            String paymentSessionId,
            String merchantKey,
            String nonce,
            String deadline,
            String payloadHash,
            String payerAddress,
            String tokenAddress,
            String commandId,
            String commandStatus,
            String txHash,
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
        this.paymentKey = paymentKey;
        this.paymentSessionId = paymentSessionId;
        this.merchantKey = merchantKey;
        this.nonce = nonce;
        this.deadline = deadline;
        this.payloadHash = payloadHash;
        this.payerAddress = payerAddress;
        this.tokenAddress = tokenAddress;
        this.commandId = commandId;
        this.commandStatus = commandStatus;
        this.txHash = txHash;
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

    public void bindStablepaySession(
            String paymentKey,
            String paymentSessionId,
            String merchantKey,
            String nonce,
            String deadline,
            String payloadHash,
            String payerAddress,
            String tokenAddress
    ) {
        this.paymentKey = paymentKey;
        this.paymentSessionId = paymentSessionId;
        this.merchantKey = merchantKey;
        this.nonce = nonce;
        this.deadline = deadline;
        this.payloadHash = payloadHash;
        this.payerAddress = payerAddress;
        this.tokenAddress = tokenAddress;
    }

    public void bindPaymentKey(String paymentKey) {
        this.paymentKey = paymentKey;
    }

    public void authorizeByWebhook(String txHash, String commandId, String commandStatus, LocalDateTime authorizedAt) {
        this.status = PaymentStatus.AUTHORIZED;
        this.txHash = txHash;
        this.commandId = commandId;
        this.commandStatus = commandStatus;
        this.reasonCode = null;
        this.authorizedAt = authorizedAt;
        this.failedAt = null;
    }

    public void failByWebhook(String reasonCode, String commandId, String commandStatus, LocalDateTime failedAt) {
        this.status = PaymentStatus.FAILED;
        this.reasonCode = reasonCode;
        this.commandId = commandId;
        this.commandStatus = commandStatus;
        this.failedAt = failedAt;
    }
}

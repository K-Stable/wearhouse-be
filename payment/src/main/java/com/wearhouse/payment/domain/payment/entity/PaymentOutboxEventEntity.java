package com.wearhouse.payment.domain.payment.entity;

import com.wearhouse.payment.domain.payment.model.PaymentOutboxStatus;
import com.wearhouse.payment.infra.jpa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "payment_outbox_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOutboxEventEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 26)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 120)
    private String topic;

    @Column(name = "partition_key", nullable = false, length = 100)
    private String partitionKey;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentOutboxStatus status;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "fail_code", length = 50)
    private String failCode;

    @Column(name = "fail_message", length = 255)
    private String failMessage;

    @Builder
    private PaymentOutboxEventEntity(
            String eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String partitionKey,
            String payload
    ) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.status = PaymentOutboxStatus.READY;
    }

    public static PaymentOutboxEventEntity ready(
            String eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String partitionKey,
            String payload
    ) {
        return new PaymentOutboxEventEntity(
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                topic,
                partitionKey,
                payload
        );
    }

    public void markSuccess() {
        this.status = PaymentOutboxStatus.SUCCESS;
        this.sentAt = LocalDateTime.now();
        this.failCode = null;
        this.failMessage = null;
    }

    public void markFailed(String failCode, String failMessage) {
        this.status = PaymentOutboxStatus.FAIL;
        this.failCode = failCode;
        this.failMessage = truncate(failMessage, 255);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

package com.wearhouse.payment.domain.payment.entity;

import com.wearhouse.payment.infra.jpa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "payment_webhook_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentWebhookEventEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 80)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private String payloadJson;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Builder
    private PaymentWebhookEventEntity(
            String eventId,
            String eventType,
            LocalDateTime occurredAt,
            LocalDateTime receivedAt,
            String payloadJson,
            String payloadHash
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
        this.payloadJson = payloadJson;
        this.payloadHash = payloadHash;
    }

    public static PaymentWebhookEventEntity received(
            String eventId,
            String eventType,
            LocalDateTime occurredAt,
            LocalDateTime receivedAt,
            String payloadJson,
            String payloadHash
    ) {
        return PaymentWebhookEventEntity.builder()
                .eventId(eventId)
                .eventType(eventType)
                .occurredAt(occurredAt)
                .receivedAt(receivedAt)
                .payloadJson(payloadJson)
                .payloadHash(payloadHash)
                .build();
    }
}

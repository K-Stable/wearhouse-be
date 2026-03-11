package com.wearhouse.inventory.domain.entity;

import com.wearhouse.inventory.domain.model.InventoryOutboxStatus;
import com.wearhouse.inventory.infra.jpa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// id,
//message_id
// aggregate_type,
// aggregate_id,
// event_type,
// payload,
// status,
// timestamp

@Entity
@Getter
@Table(name = "inventory_outbox_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryOutboxEventEntity extends BaseEntity {

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
    private InventoryOutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    @Builder
    private InventoryOutboxEventEntity(
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
        this.status = InventoryOutboxStatus.READY;
        this.retryCount = 0;
    }

    public static InventoryOutboxEventEntity ready(
            String eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String partitionKey,
            String payload
    ) {
        return new InventoryOutboxEventEntity(
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
        this.status = InventoryOutboxStatus.SEND_SUCCESS;
        this.publishedAt = LocalDateTime.now();
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markFailed(int retryCount, LocalDateTime nextRetryAt, String errorCode, String errorMessage) {
        this.status = InventoryOutboxStatus.SEND_FAIL;
        this.retryCount = retryCount;
        this.nextRetryAt = nextRetryAt;
        this.errorCode = errorCode;
        this.errorMessage = truncate(errorMessage, 255);
    }

    public void markDead(int retryCount, String errorCode, String errorMessage) {
        this.status = InventoryOutboxStatus.DEAD;
        this.retryCount = retryCount;
        this.errorCode = errorCode;
        this.errorMessage = truncate(errorMessage, 255);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}


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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

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

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "fail_message", length = 255)
    private String failMessage;

    @Builder
    private InventoryOutboxEventEntity(
            String eventId,
            String eventType,
            String topic,
            String partitionKey,
            String payload
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.status = InventoryOutboxStatus.READY;
    }

    public static InventoryOutboxEventEntity ready(
            String eventId,
            String eventType,
            String topic,
            String partitionKey,
            String payload
    ) {
        return new InventoryOutboxEventEntity(
                eventId,
                eventType,
                topic,
                partitionKey,
                payload
        );
    }

    public void markSuccess() {
        this.status = InventoryOutboxStatus.SUCCESS;
        this.sentAt = LocalDateTime.now();
        this.failMessage = null;
    }

    public void markFailed(String failMessage) {
        this.status = InventoryOutboxStatus.FAIL;
        this.failMessage = truncate(failMessage, 255);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

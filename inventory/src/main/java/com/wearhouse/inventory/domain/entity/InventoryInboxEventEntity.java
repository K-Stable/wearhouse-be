package com.wearhouse.inventory.domain.entity;

import com.wearhouse.inventory.domain.model.InventoryInboxStatus;
import com.wearhouse.inventory.infra.jpa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "inventory_inbox_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_inbox_event_consumer",
                columnNames = {"event_id", "consumer_name"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryInboxEventEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 26)
    private String eventId;

    @Column(name = "consumer_name", nullable = false, length = 80)
    private String consumerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InventoryInboxStatus status;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Builder
    private InventoryInboxEventEntity(
            String eventId,
            String consumerName
    ) {
        this.eventId = eventId;
        this.consumerName = consumerName;
        this.status = InventoryInboxStatus.RECEIVED;
    }

    public static InventoryInboxEventEntity received(
            String eventId,
            String consumerName
    ) {
        return new InventoryInboxEventEntity(eventId, consumerName);
    }

    public void markProcessed() {
        this.status = InventoryInboxStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = InventoryInboxStatus.FAILED;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public InventoryInboxStatus getStatus() {
        return status;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}

package com.wearhouse.order.domain.entity;

import com.wearhouse.order.domain.model.OrderInboxStatus;
import com.wearhouse.order.infra.jpa.common.BaseEntity;
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

@Entity
@Getter
@Table(name = "order_inbox_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderInboxEventEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 26)
    private String eventId;

    @Column(name = "consumer_name", nullable = false, length = 80)
    private String consumerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderInboxStatus status;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Builder
    private OrderInboxEventEntity(
            String eventId,
            String consumerName
    ) {
        this.eventId = eventId;
        this.consumerName = consumerName;
        this.status = OrderInboxStatus.RECEIVED;
    }

    public static OrderInboxEventEntity received(
            String eventId,
            String consumerName
    ) {
        return new OrderInboxEventEntity(eventId, consumerName);
    }

    public void markProcessed() {
        this.status = OrderInboxStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = OrderInboxStatus.FAILED;
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

    public OrderInboxStatus getStatus() {
        return status;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}

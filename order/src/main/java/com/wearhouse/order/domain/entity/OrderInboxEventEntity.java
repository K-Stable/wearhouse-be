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

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 120)
    private String topic;

    @Column(name = "partition_key", length = 100)
    private String partitionKey;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderInboxStatus status;

    @Column(name = "fail_count", nullable = false)
    private Integer failCount;

    @Column(name = "fail_reason_code", length = 50)
    private String failReasonCode;

    @Column(name = "fail_reason_message", length = 255)
    private String failReasonMessage;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Builder
    private OrderInboxEventEntity(
            String eventId,
            String consumerName,
            String eventType,
            String topic,
            String partitionKey,
            String payload
    ) {
        this.eventId = eventId;
        this.consumerName = consumerName;
        this.eventType = eventType;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.status = OrderInboxStatus.RECEIVED;
        this.failCount = 0;
    }

    public static OrderInboxEventEntity received(
            String eventId,
            String consumerName,
            String eventType,
            String topic,
            String partitionKey,
            String payload
    ) {
        return new OrderInboxEventEntity(eventId, consumerName, eventType, topic, partitionKey, payload);
    }

    public void markProcessed() {
        this.status = OrderInboxStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
    }

    public void markFailed(String reasonCode, String reasonMessage) {
        this.status = OrderInboxStatus.FAILED;
        this.failCount = this.failCount + 1;
        this.failReasonCode = reasonCode;
        this.failReasonMessage = truncate(reasonMessage, 255);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
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

    public String getEventType() {
        return eventType;
    }

    public String getTopic() {
        return topic;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public String getPayload() {
        return payload;
    }

    public OrderInboxStatus getStatus() {
        return status;
    }

    public Integer getFailCount() {
        return failCount;
    }

    public String getFailReasonCode() {
        return failReasonCode;
    }

    public String getFailReasonMessage() {
        return failReasonMessage;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}

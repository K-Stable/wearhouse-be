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

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "topic", length = 120)
    private String topic;

    @Column(name = "partition_key", length = 100)
    private String partitionKey;

    @Column(name = "payload", columnDefinition = "json")
    private String payload;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_no", length = 40)
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderInboxStatus status;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "fail_reason_code", length = 50)
    private String failReasonCode;

    @Column(name = "fail_reason_message", length = 255)
    private String failReasonMessage;

    private OrderInboxEventEntity(
            String eventId,
            String consumerName,
            String eventType,
            String topic,
            String partitionKey,
            String payload,
            Long orderId,
            String orderNo
    ) {
        this.eventId = eventId;
        this.consumerName = consumerName;
        this.eventType = eventType;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.status = OrderInboxStatus.RECEIVED;
    }

    public static OrderInboxEventEntity received(
            String eventId,
            String consumerName,
            String eventType,
            String topic,
            String partitionKey,
            String payload,
            Long orderId,
            String orderNo
    ) {
        return new OrderInboxEventEntity(eventId, consumerName, eventType, topic, partitionKey, payload, orderId, orderNo);
    }

    public void markProcessed() {
        this.status = OrderInboxStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
        this.failReasonCode = null;
        this.failReasonMessage = null;
    }

    public void markFailed(String reasonCode, String reasonMessage) {
        this.status = OrderInboxStatus.FAILED;
        this.failReasonCode = reasonCode;
        this.failReasonMessage = truncate(reasonMessage, 255);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

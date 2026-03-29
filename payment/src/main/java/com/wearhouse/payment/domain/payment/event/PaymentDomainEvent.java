package com.wearhouse.payment.domain.payment.event;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class PaymentDomainEvent {

    private final String eventId;
    private final String eventType;
    private final String aggregateType;
    private final String aggregateId;
    private final String topic;
    private final String partitionKey;
    private final String producer;
    private final Integer version;
    private final LocalDateTime occurredAt;
    private final Object payload;

    private PaymentDomainEvent(Builder builder) {
        this.eventId = builder.eventId;
        this.eventType = builder.eventType;
        this.aggregateType = builder.aggregateType;
        this.aggregateId = builder.aggregateId;
        this.topic = builder.topic;
        this.partitionKey = builder.partitionKey;
        this.producer = builder.producer;
        this.version = builder.version;
        this.occurredAt = builder.occurredAt;
        this.payload = builder.payload;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getTopic() {
        return topic;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public Map<String, Object> toEnvelope() {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("aggregateType", aggregateType);
        envelope.put("aggregateId", aggregateId);
        envelope.put("occurredAt", occurredAt);
        envelope.put("version", version);
        envelope.put("producer", producer);
        envelope.put("payload", payload);
        return envelope;
    }

    public static final class Builder {
        private String eventId;
        private String eventType;
        private String aggregateType;
        private String aggregateId;
        private String topic;
        private String partitionKey;
        private String producer = "payment-service";
        private Integer version = 1;
        private LocalDateTime occurredAt = LocalDateTime.now();
        private Object payload = new LinkedHashMap<>();

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder aggregateType(String aggregateType) {
            this.aggregateType = aggregateType;
            return this;
        }

        public Builder aggregateId(String aggregateId) {
            this.aggregateId = aggregateId;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder partitionKey(String partitionKey) {
            this.partitionKey = partitionKey;
            return this;
        }

        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }

        public PaymentDomainEvent build() {
            return new PaymentDomainEvent(this);
        }
    }
}

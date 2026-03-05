package com.wearhouse.order.global.kafka.dto;

public class KafkaTestPublishResponse {

    private final String topic;
    private final String key;
    private final String message;
    private final String publishedAt;

    private KafkaTestPublishResponse(Builder builder) {
        this.topic = builder.topic;
        this.key = builder.key;
        this.message = builder.message;
        this.publishedAt = builder.publishedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTopic() {
        return topic;
    }

    public String getKey() {
        return key;
    }

    public String getMessage() {
        return message;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public static final class Builder {
        private String topic;
        private String key;
        private String message;
        private String publishedAt;

        private Builder() {
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder publishedAt(String publishedAt) {
            this.publishedAt = publishedAt;
            return this;
        }

        public KafkaTestPublishResponse build() {
            return new KafkaTestPublishResponse(this);
        }
    }
}

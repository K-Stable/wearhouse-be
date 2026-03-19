package com.wearhouse.common.support.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wearhouse.kafka.error-handler")
public class KafkaErrorHandlerProperties {

    private long retryIntervalMs = 2000L;
    private long retryAttempts = 3L;
    private String dltTopic = "wearhouse.kafka.failure.v1";

    public long getRetryIntervalMs() {
        return retryIntervalMs;
    }

    public void setRetryIntervalMs(long retryIntervalMs) {
        this.retryIntervalMs = retryIntervalMs;
    }

    public long getRetryAttempts() {
        return retryAttempts;
    }

    public void setRetryAttempts(long retryAttempts) {
        this.retryAttempts = retryAttempts;
    }

    public String getDltTopic() {
        return dltTopic;
    }

    public void setDltTopic(String dltTopic) {
        this.dltTopic = dltTopic;
    }
}

package com.wearhouse.payment.support.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentRuntimePropertiesValidator {

    private final String paymentPrepareTopic;
    private final String paymentEventTopic;
    private final long paymentKafkaSendTimeoutMs;
    private final int pendingTimeoutMinutes;
    private final long timeoutCheckIntervalMs;
    private final int timeoutBatchSize;

    public PaymentRuntimePropertiesValidator(
            @Value("${wearhouse.kafka.payment-prepare-topic:}") String paymentPrepareTopic,
            @Value("${wearhouse.kafka.payment-event-topic:}") String paymentEventTopic,
            @Value("${wearhouse.payment.kafka.send-timeout-ms:0}") long paymentKafkaSendTimeoutMs,
            @Value("${wearhouse.payment.mock.pending-timeout-minutes:0}") int pendingTimeoutMinutes,
            @Value("${wearhouse.payment.mock.timeout-check-interval-ms:0}") long timeoutCheckIntervalMs,
            @Value("${wearhouse.payment.mock.timeout-batch-size:0}") int timeoutBatchSize
    ) {
        this.paymentPrepareTopic = paymentPrepareTopic;
        this.paymentEventTopic = paymentEventTopic;
        this.paymentKafkaSendTimeoutMs = paymentKafkaSendTimeoutMs;
        this.pendingTimeoutMinutes = pendingTimeoutMinutes;
        this.timeoutCheckIntervalMs = timeoutCheckIntervalMs;
        this.timeoutBatchSize = timeoutBatchSize;
    }

    @PostConstruct
    void validate() {
        requireText("wearhouse.kafka.payment-prepare-topic", paymentPrepareTopic);
        requireText("wearhouse.kafka.payment-event-topic", paymentEventTopic);
        requirePositive("wearhouse.payment.kafka.send-timeout-ms", paymentKafkaSendTimeoutMs);
        requirePositive("wearhouse.payment.mock.pending-timeout-minutes", pendingTimeoutMinutes);
        requirePositive("wearhouse.payment.mock.timeout-check-interval-ms", timeoutCheckIntervalMs);
        requirePositive("wearhouse.payment.mock.timeout-batch-size", timeoutBatchSize);
    }

    private void requireText(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 값은 비어 있을 수 없습니다.");
        }
    }

    private void requirePositive(String key, long value) {
        if (value <= 0) {
            throw new IllegalStateException(key + " 값은 1 이상이어야 합니다.");
        }
    }
}

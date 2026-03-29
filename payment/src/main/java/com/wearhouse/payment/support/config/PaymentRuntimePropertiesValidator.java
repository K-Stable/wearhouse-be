package com.wearhouse.payment.support.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class PaymentRuntimePropertiesValidator {

    private final PaymentKafkaTopicsProperties paymentKafkaTopicsProperties;
    private final PaymentKafkaRuntimeProperties paymentKafkaRuntimeProperties;
    private final PaymentMockProperties paymentMockProperties;
    private final PaymentOrderInternalProperties paymentOrderInternalProperties;
    private final PaymentPayProperties paymentPayProperties;
    private final PaymentWebhookProperties paymentWebhookProperties;

    public PaymentRuntimePropertiesValidator(
            PaymentKafkaTopicsProperties paymentKafkaTopicsProperties,
            PaymentKafkaRuntimeProperties paymentKafkaRuntimeProperties,
            PaymentMockProperties paymentMockProperties,
            PaymentOrderInternalProperties paymentOrderInternalProperties,
            PaymentPayProperties paymentPayProperties,
            PaymentWebhookProperties paymentWebhookProperties
    ) {
        this.paymentKafkaTopicsProperties = paymentKafkaTopicsProperties;
        this.paymentKafkaRuntimeProperties = paymentKafkaRuntimeProperties;
        this.paymentMockProperties = paymentMockProperties;
        this.paymentOrderInternalProperties = paymentOrderInternalProperties;
        this.paymentPayProperties = paymentPayProperties;
        this.paymentWebhookProperties = paymentWebhookProperties;
    }

    @PostConstruct
    void validate() {
        requireText("wearhouse.kafka.payment-prepare-topic", paymentKafkaTopicsProperties.paymentPrepareTopic());
        requireText("wearhouse.kafka.payment-event-topic", paymentKafkaTopicsProperties.paymentEventTopic());
        requirePositive("wearhouse.payment.kafka.send-timeout-ms", paymentKafkaRuntimeProperties.sendTimeoutMs());
        requirePositive("wearhouse.payment.mock.pending-timeout-minutes", paymentMockProperties.pendingTimeoutMinutes());
        requirePositive("wearhouse.payment.mock.timeout-check-interval-ms", paymentMockProperties.timeoutCheckIntervalMs());
        requirePositive("wearhouse.payment.mock.timeout-batch-size", paymentMockProperties.timeoutBatchSize());
        requireText("wearhouse.payment.mock.fail-methods", paymentMockProperties.failMethods());
        requireText("wearhouse.payment.mock.timeout-methods", paymentMockProperties.timeoutMethods());
        requireText("wearhouse.order.internal.shared-secret", paymentOrderInternalProperties.sharedSecret());
        requireText("wearhouse.pay.webhook.secret", paymentWebhookProperties.secret());
        requireNonNegative("wearhouse.pay.webhook.allowed-skew-seconds", paymentWebhookProperties.allowedSkewSeconds());
        requireText("wearhouse.pay.api-base-url", paymentPayProperties.apiBaseUrl());
        requireText("wearhouse.pay.secret-key", paymentPayProperties.secretKey());
        requireText("wearhouse.pay.prepare-path", paymentPayProperties.preparePath());
        requireText("wearhouse.pay.confirm-path", paymentPayProperties.confirmPath());
        requirePositive("wearhouse.pay.timeout-ms", paymentPayProperties.timeoutMs());
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

    private void requireNonNegative(String key, long value) {
        if (value < 0) {
            throw new IllegalStateException(key + " 값은 0 이상이어야 합니다.");
        }
    }
}

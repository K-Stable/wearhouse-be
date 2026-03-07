package com.wearhouse.payment.support.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentKafkaFlowMetrics {

    private final MeterRegistry meterRegistry;

    public PaymentKafkaFlowMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incrementConsumerHandled(String source, String eventType, String topic, String result) {
        counter("wearhouse_payment_kafka_consume_total",
                "source", sanitize(source),
                "event_type", sanitize(eventType),
                "topic", sanitize(topic),
                "result", sanitize(result))
                .increment();
    }

    public void incrementPublishAttempt(String eventType, String topic) {
        counter("wearhouse_payment_event_publish_attempt_total",
                "event_type", sanitize(eventType),
                "topic", sanitize(topic))
                .increment();
    }

    public void incrementPublishSuccess(String eventType, String topic) {
        counter("wearhouse_payment_event_publish_success_total",
                "event_type", sanitize(eventType),
                "topic", sanitize(topic))
                .increment();
    }

    public void incrementPublishFailure(String eventType, String topic) {
        counter("wearhouse_payment_event_publish_failure_total",
                "event_type", sanitize(eventType),
                "topic", sanitize(topic))
                .increment();
    }

    public void incrementPaymentDecision(String result) {
        counter("wearhouse_payment_decision_total", "result", sanitize(result)).increment();
    }

    public void incrementTimeoutFailed(int failedCount) {
        if (failedCount <= 0) {
            return;
        }
        counter("wearhouse_payment_timeout_failed_total").increment(failedCount);
    }

    private Counter counter(String metricName, String... tags) {
        return Counter.builder(metricName).tags(tags).register(meterRegistry);
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value;
    }
}

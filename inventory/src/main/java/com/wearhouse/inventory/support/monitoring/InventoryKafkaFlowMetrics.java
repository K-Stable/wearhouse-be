package com.wearhouse.inventory.support.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class InventoryKafkaFlowMetrics {

    private final MeterRegistry meterRegistry;

    public InventoryKafkaFlowMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incrementConsumerHandled(String source, String eventType, String topic, String result) {
        counter("wearhouse_inventory_kafka_consume_total",
                "source", sanitize(source),
                "event_type", sanitize(eventType),
                "topic", sanitize(topic),
                "result", sanitize(result))
                .increment();
    }

    public void incrementPublishAttempt(String eventType, String topic) {
        counter("wearhouse_inventory_event_publish_attempt_total",
                "event_type", sanitize(eventType),
                "topic", sanitize(topic))
                .increment();
    }

    public void incrementPublishSuccess(String eventType, String topic) {
        counter("wearhouse_inventory_event_publish_success_total",
                "event_type", sanitize(eventType),
                "topic", sanitize(topic))
                .increment();
    }

    public void incrementPublishFailure(String eventType, String topic) {
        counter("wearhouse_inventory_event_publish_failure_total",
                "event_type", sanitize(eventType),
                "topic", sanitize(topic))
                .increment();
    }

    public void incrementConcurrencyGuard(String control, String result) {
        counter("wearhouse_inventory_concurrency_guard_total",
                "control", sanitize(control),
                "result", sanitize(result))
                .increment();
    }

    public void incrementExpiredReleaseCount(int releasedCount) {
        if (releasedCount <= 0) {
            return;
        }
        counter("wearhouse_inventory_reservation_expired_release_total").increment(releasedCount);
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

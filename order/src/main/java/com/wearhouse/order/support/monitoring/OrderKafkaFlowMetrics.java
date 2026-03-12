package com.wearhouse.order.support.monitoring;

import com.wearhouse.order.domain.model.OrderOutboxStatus;
import com.wearhouse.order.infra.jpa.repository.OrderOutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderKafkaFlowMetrics {

    private final MeterRegistry meterRegistry;

    public OrderKafkaFlowMetrics(MeterRegistry meterRegistry, OrderOutboxEventRepository outboxEventJpaRepository) {
        this.meterRegistry = meterRegistry;
        registerOutboxGauge(outboxEventJpaRepository, OrderOutboxStatus.READY, "ready");
        registerOutboxGauge(outboxEventJpaRepository, OrderOutboxStatus.FAIL, "fail");
        registerOutboxGauge(outboxEventJpaRepository, OrderOutboxStatus.SUCCESS, "success");
    }

    public void incrementOutboxRecorded(String eventType, String topic) {
        counter("wearhouse_order_outbox_record_total",
                "event_type", sanitize(eventType),
                "topic", sanitize(topic))
                .increment();
    }

    public void incrementPublishAttempt(String eventType, String topic, String trigger) {
        counter("wearhouse_order_outbox_publish_attempt_total",
                "event_type", sanitize(eventType),
                "topic", sanitize(topic),
                "trigger", sanitize(trigger))
                .increment();
    }

    public void incrementPublishSuccess(String eventType, String topic, String trigger) {
        counter("wearhouse_order_outbox_publish_success_total",
                "event_type", sanitize(eventType),
                "topic", sanitize(topic),
                "trigger", sanitize(trigger))
                .increment();
    }

    public void incrementPublishFailure(String eventType, String topic, String trigger, String result) {
        counter("wearhouse_order_outbox_publish_failure_total",
                "event_type", sanitize(eventType),
                "topic", sanitize(topic),
                "trigger", sanitize(trigger),
                "result", sanitize(result))
                .increment();
    }

    public void incrementConsumerHandled(String source, String eventType, String topic, String result) {
        counter("wearhouse_order_kafka_consume_total",
                "source", sanitize(source),
                "event_type", sanitize(eventType),
                "topic", sanitize(topic),
                "result", sanitize(result))
                .increment();
    }

    private void registerOutboxGauge(
            OrderOutboxEventRepository outboxEventJpaRepository,
            OrderOutboxStatus status,
            String statusLabel
    ) {
        Gauge.builder(
                        "wearhouse_order_outbox_event_count",
                        outboxEventJpaRepository,
                        r -> countByStatusSafely(r, status)
                )
                .description("order outbox event count by status")
                .tag("status", statusLabel)
                .register(meterRegistry);
    }

    private double countByStatusSafely(OrderOutboxEventRepository repository, OrderOutboxStatus status) {
        try {
            return repository.countByStatus(status);
        } catch (Exception ignored) {
            return 0;
        }
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

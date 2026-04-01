package com.wearhouse.order.support.monitoring;

import com.wearhouse.order.domain.model.OrderSagaState;
import com.wearhouse.order.domain.model.OrderStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class OrderFlowMetrics {

    private final MeterRegistry meterRegistry;

    public OrderFlowMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordStatusTransition(
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String reasonCode
    ) {
        counter(
                "wearhouse_order_status_transition_total",
                "from_status", statusValue(fromStatus),
                "to_status", statusValue(toStatus),
                "reason_code", sanitize(reasonCode)
        ).increment();
    }

    public void recordSagaTransition(
            OrderSagaState fromState,
            OrderSagaState toState,
            String reasonCode
    ) {
        counter(
                "wearhouse_order_saga_transition_total",
                "from_state", sagaValue(fromState),
                "to_state", sagaValue(toState),
                "reason_code", sanitize(reasonCode)
        ).increment();
    }

    public void recordPrepareWait(
            OrderStatus initialStatus,
            OrderStatus resolvedStatus,
            boolean timedOut,
            long waitedMs
    ) {
        counter(
                "wearhouse_order_payment_prepare_wait_total",
                "initial_status", statusValue(initialStatus),
                "resolved_status", statusValue(resolvedStatus),
                "result", timedOut ? "timeout" : "resolved"
        ).increment();

        if (waitedMs <= 0) {
            return;
        }
        Timer.builder("wearhouse_order_payment_prepare_wait_seconds")
                .description("Time spent waiting until order becomes payment-preparable")
                .tag("result", timedOut ? "timeout" : "resolved")
                .register(meterRegistry)
                .record(Duration.ofMillis(waitedMs));
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name)
                .tags(tags)
                .register(meterRegistry);
    }

    private String statusValue(OrderStatus status) {
        return status == null ? "none" : status.name().toLowerCase();
    }

    private String sagaValue(OrderSagaState state) {
        return state == null ? "none" : state.name().toLowerCase();
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value.trim().toLowerCase();
    }
}


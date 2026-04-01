package com.wearhouse.order.support.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderInventoryReservationMetrics {

    private final MeterRegistry meterRegistry;

    public void recordReserveRequested() {
        counter("requested").increment();
    }

    public void recordReserveSucceeded() {
        counter("reserved").increment();
    }

    public void recordReserveFailed() {
        counter("failed").increment();
    }

    private Counter counter(String result) {
        return Counter.builder("wearhouse_order_inventory_reserve_result_total")
                .description("Order inventory reservation flow result counters")
                .tag("result", sanitize(result))
                .register(meterRegistry);
    }

    private String sanitize(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim().toLowerCase();
    }
}


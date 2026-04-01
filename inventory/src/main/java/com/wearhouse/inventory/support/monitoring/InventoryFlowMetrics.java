package com.wearhouse.inventory.support.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class InventoryFlowMetrics {

    private final MeterRegistry meterRegistry;

    public InventoryFlowMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordReserveRequested() {
        reserveCounter("requested", "none").increment();
    }

    public void recordReserveSucceeded() {
        reserveCounter("reserved", "none").increment();
    }

    public void recordReserveFailed(String reasonCode) {
        reserveCounter("failed", sanitize(reasonCode)).increment();
    }

    public void recordLockAcquire(String result, long elapsedMs) {
        lockAcquireCounter(sanitize(result)).increment();
        if (elapsedMs > 0) {
            Timer.builder("wearhouse_inventory_lock_acquire_seconds")
                    .description("Elapsed time for inventory lock acquisition")
                    .tag("result", sanitize(result))
                    .register(meterRegistry)
                    .record(Duration.ofMillis(elapsedMs));
        }
    }

    public void recordLockBatch(String result, int lockCount, long holdMs) {
        String normalized = sanitize(result);
        Counter.builder("wearhouse_inventory_lock_batch_total")
                .tag("result", normalized)
                .register(meterRegistry)
                .increment();

        DistributionSummary.builder("wearhouse_inventory_lock_batch_size")
                .description("Number of SKU locks acquired per batch operation")
                .tag("result", normalized)
                .register(meterRegistry)
                .record(Math.max(lockCount, 0));

        if (holdMs > 0) {
            Timer.builder("wearhouse_inventory_lock_hold_seconds")
                    .description("Time spent executing with held inventory locks")
                    .tag("result", normalized)
                    .register(meterRegistry)
                    .record(Duration.ofMillis(holdMs));
        }
    }

    private Counter reserveCounter(String result, String reasonCode) {
        return Counter.builder("wearhouse_inventory_reserve_result_total")
                .description("Inventory reservation result counters")
                .tag("result", sanitize(result))
                .tag("reason_code", sanitize(reasonCode))
                .register(meterRegistry);
    }

    private Counter lockAcquireCounter(String result) {
        return Counter.builder("wearhouse_inventory_lock_acquire_total")
                .description("Inventory hot-SKU lock acquire result counters")
                .tag("result", sanitize(result))
                .register(meterRegistry);
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value.trim().toLowerCase();
    }
}


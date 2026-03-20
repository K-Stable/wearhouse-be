package com.wearhouse.inventory.support.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "wearhouse.inventory")
public class InventoryProperties {

    @Positive
    private int reservationHoldMinutes = 15;

    @Positive
    private int optimisticRetryCount = 3;

    @Positive
    private long reservationExpireIntervalMs = 30000L;

    @Positive
    private int reservationExpireBatchSize = 200;

    private String hotSkus = "";

    @Valid
    private Lock lock = new Lock();

    @Valid
    private Cache cache = new Cache();

    @Valid
    private Internal internal = new Internal();

    @Getter
    @Setter
    public static class Lock {
        @Positive
        private long waitTimeMs = 1200L;
        @Positive
        private long leaseTimeMs = 3000L;
        @Positive
        private long retryIntervalMs = 40L;
        @NotBlank
        private String keyPrefix = "inventory:lock:sku:";
    }

    @Getter
    @Setter
    public static class Cache {
        @Positive
        private long stockTtlSeconds = 30L;
        @NotBlank
        private String stockKeyPrefix = "inventory:stock:available:";
    }

    @Getter
    @Setter
    public static class Internal {
        @NotBlank
        private String sharedSecret = "wearhouse-inventory-internal-secret";
    }
}

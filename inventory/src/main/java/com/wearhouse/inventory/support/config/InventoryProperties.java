package com.wearhouse.inventory.support.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "wearhouse.inventory")
public record InventoryProperties(
        @Positive
        @DefaultValue("15")
        int reservationHoldMinutes,

        @Positive
        @DefaultValue("3")
        int optimisticRetryCount,

        @Positive
        @DefaultValue("30000")
        long reservationExpireIntervalMs,

        @Positive
        @DefaultValue("200")
        int reservationExpireBatchSize,

        @DefaultValue("")
        String hotSkus,

        @Valid
        @DefaultValue
        Lock lock,

        @Valid
        @DefaultValue
        Cache cache,

        @Valid
        @DefaultValue
        Internal internal
) {
    public record Lock(
            @Positive
            @DefaultValue("1200")
            long waitTimeMs,
            @Positive
            @DefaultValue("3000")
            long leaseTimeMs,
            @Positive
            @DefaultValue("40")
            long retryIntervalMs,
            @NotBlank
            @DefaultValue("inventory:lock:sku:")
            String keyPrefix
    ) {
    }

    public record Cache(
            @Positive
            @DefaultValue("30")
            long stockTtlSeconds,
            @NotBlank
            @DefaultValue("inventory:stock:available:")
            String stockKeyPrefix
    ) {
    }

    public record Internal(
            @NotBlank
            @DefaultValue("wearhouse-inventory-internal-secret")
            String sharedSecret
    ) {
    }
}

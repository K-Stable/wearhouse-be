package com.wearhouse.order.support.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "wearhouse.inventory.internal")
public record OrderInventoryInternalProperties(
        @NotBlank
        @DefaultValue("wearhouse-inventory-internal-secret")
        String sharedSecret
) {
}

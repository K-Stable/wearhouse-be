package com.wearhouse.order.support.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "wearhouse.user.internal")
public record OrderUserInternalProperties(
        @NotBlank
        @DefaultValue("wearhouse-user-internal-secret")
        String sharedSecret
) {
}

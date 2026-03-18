package com.wearhouse.product.support.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "wearhouse.product.image")
public class ProductImageStorageProperties {

    private String keyPrefix = "dev";
}

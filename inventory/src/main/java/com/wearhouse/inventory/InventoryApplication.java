package com.wearhouse.inventory;

import com.wearhouse.inventory.support.config.InventoryKafkaTopicsProperties;
import com.wearhouse.inventory.support.config.InventoryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties({
        InventoryKafkaTopicsProperties.class,
        InventoryProperties.class
})
@SpringBootApplication(scanBasePackages = "com.wearhouse")
@EnableFeignClients(basePackages = {
        "com.wearhouse.inventory",
        "com.wearhouse.common.infra.feign.product"
})
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}

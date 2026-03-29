package com.wearhouse.order;

import com.wearhouse.common.support.config.OutboxProperties;
import com.wearhouse.order.support.config.OrderKafkaTopicsProperties;
import com.wearhouse.order.support.config.OrderInternalProperties;
import com.wearhouse.order.support.config.OrderInventoryInternalProperties;
import com.wearhouse.order.support.config.OrderProperties;
import com.wearhouse.order.support.config.OrderUserInternalProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties({
        OrderKafkaTopicsProperties.class,
        OrderProperties.class,
        OrderInternalProperties.class,
        OrderInventoryInternalProperties.class,
        OrderUserInternalProperties.class,
        OutboxProperties.class
})
@EnableFeignClients(basePackages = {"com.wearhouse.common.infra.feign", "com.wearhouse.order.payment.client"})
@SpringBootApplication(scanBasePackages = "com.wearhouse")
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}

package com.wearhouse.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.wearhouse")
@EnableFeignClients(basePackages = {
        "com.wearhouse.product",
        "com.wearhouse.common.infra.feign.inventory"
})
public class ProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}

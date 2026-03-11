package com.wearhouse.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.wearhouse")
@EnableFeignClients(basePackages = {
        "com.wearhouse.user",
        "com.wearhouse.common.infra.feign.orderquery"
})
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}

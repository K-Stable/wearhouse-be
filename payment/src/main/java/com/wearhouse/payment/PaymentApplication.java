package com.wearhouse.payment;

import com.wearhouse.common.support.config.OutboxProperties;
import com.wearhouse.payment.support.config.PaymentKafkaTopicsProperties;
import com.wearhouse.payment.support.config.PaymentKafkaRuntimeProperties;
import com.wearhouse.payment.support.config.PaymentMockProperties;
import com.wearhouse.payment.support.config.PaymentOrderInternalProperties;
import com.wearhouse.payment.support.config.PaymentPayProperties;
import com.wearhouse.payment.support.config.PaymentWebhookProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties({
        PaymentKafkaTopicsProperties.class,
        PaymentKafkaRuntimeProperties.class,
        PaymentMockProperties.class,
        PaymentOrderInternalProperties.class,
        PaymentPayProperties.class,
        PaymentWebhookProperties.class,
        OutboxProperties.class
})
@SpringBootApplication(scanBasePackages = "com.wearhouse")
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}

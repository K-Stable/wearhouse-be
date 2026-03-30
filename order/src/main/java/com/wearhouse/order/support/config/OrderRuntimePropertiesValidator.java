package com.wearhouse.order.support.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderRuntimePropertiesValidator {

    private final OrderKafkaTopicsProperties kafkaTopicsProperties;
    private final OrderProperties orderProperties;
    private final OrderInternalProperties orderInternalProperties;
    private final OrderInventoryInternalProperties orderInventoryInternalProperties;
    private final OrderUserInternalProperties orderUserInternalProperties;

    @PostConstruct
    void validate() {
        requireText("wearhouse.kafka.inventory-reserve-topic", kafkaTopicsProperties.inventoryReserveTopic());
        requireText("wearhouse.kafka.inventory-command-topic", kafkaTopicsProperties.inventoryCommandTopic());
        requireText("wearhouse.kafka.inventory-event-topic", kafkaTopicsProperties.inventoryEventTopic());
        requireText("wearhouse.kafka.payment-prepare-topic", kafkaTopicsProperties.paymentPrepareTopic());
        requireText("wearhouse.kafka.payment-event-topic", kafkaTopicsProperties.paymentEventTopic());
        requireText("wearhouse.kafka.order-event-topic", kafkaTopicsProperties.orderEventTopic());
        requirePositive("wearhouse.order.payment-confirm-wait-timeout-ms", orderProperties.paymentConfirmWaitTimeoutMs());
        requirePositive("wearhouse.order.payment-confirm-wait-interval-ms", orderProperties.paymentConfirmWaitIntervalMs());
        requirePositive("wearhouse.order.payment-prepare-wait-timeout-ms", orderProperties.paymentPrepareWaitTimeoutMs());
        requirePositive("wearhouse.order.payment-prepare-wait-interval-ms", orderProperties.paymentPrepareWaitIntervalMs());
        requirePositive("wearhouse.order.delivery-purchase-confirm-delay-days", orderProperties.deliveryPurchaseConfirmDelayDays());
        requirePositive(
                "wearhouse.order.delivery-purchase-confirm-scheduler-interval-ms",
                orderProperties.deliveryPurchaseConfirmSchedulerIntervalMs()
        );
        requireText("wearhouse.order.payment-prepare-success-url-template", orderProperties.paymentPrepareSuccessUrlTemplate());
        requireText("wearhouse.order.payment-prepare-fail-url-template", orderProperties.paymentPrepareFailUrlTemplate());
        requireText("wearhouse.order.internal.shared-secret", orderInternalProperties.sharedSecret());
        requireText("wearhouse.inventory.internal.shared-secret", orderInventoryInternalProperties.sharedSecret());
        requireText("wearhouse.user.internal.shared-secret", orderUserInternalProperties.sharedSecret());
    }

    private void requireText(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 값은 비어 있을 수 없습니다.");
        }
    }

    private void requirePositive(String key, long value) {
        if (value <= 0) {
            throw new IllegalStateException(key + " 값은 1 이상이어야 합니다.");
        }
    }

}

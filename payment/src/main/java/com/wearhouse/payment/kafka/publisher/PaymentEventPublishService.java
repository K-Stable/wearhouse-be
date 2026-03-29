package com.wearhouse.payment.kafka.publisher;

import com.wearhouse.payment.domain.payment.event.PaymentDomainEvent;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEventPublisher;
import com.wearhouse.payment.support.PaymentIdGenerator;
import com.wearhouse.payment.support.config.PaymentKafkaTopicsProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentEventPublishService {

    private static final String PAYMENT_AUTHORIZED_EVENT = "PaymentAuthorized";
    private static final String PAYMENT_FAILED_EVENT = "PaymentFailed";

    private final PaymentDomainEventPublisher paymentDomainEventPublisher;
    private final PaymentKafkaTopicsProperties paymentKafkaTopicsProperties;

    public void publishAuthorized(
            Long orderId,
            String orderNo,
            String paymentId,
            BigDecimal amount,
            String paymentMethod,
            LocalDateTime authorizedAt
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderNo", orderNo);
        payload.put("paymentId", paymentId);
        payload.put("amount", amount);
        payload.put("method", paymentMethod);
        payload.put("authorizedAt", authorizedAt);
        publish(orderId, PAYMENT_AUTHORIZED_EVENT, payload);
    }

    public void publishAuthorizedFromWebhook(
            Long orderId,
            String orderNo,
            String paymentId,
            String paymentKey,
            String txHash,
            LocalDateTime authorizedAt
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderNo", orderNo);
        payload.put("paymentId", paymentId);
        payload.put("paymentKey", paymentKey);
        payload.put("txHash", txHash);
        payload.put("authorizedAt", authorizedAt);
        publish(orderId, PAYMENT_AUTHORIZED_EVENT, payload);
    }

    public void publishFailed(
            Long orderId,
            String orderNo,
            String paymentId,
            String reasonCode,
            String reasonMessage,
            LocalDateTime failedAt
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderNo", orderNo);
        payload.put("paymentId", paymentId);
        payload.put("reasonCode", reasonCode);
        payload.put("reasonMessage", reasonMessage);
        payload.put("failedAt", failedAt);
        publish(orderId, PAYMENT_FAILED_EVENT, payload);
    }

    private void publish(Long orderId, String eventType, Map<String, Object> payload) {
        PaymentDomainEvent event = PaymentDomainEvent.builder()
                .eventId(PaymentIdGenerator.newEventId())
                .eventType(eventType)
                .aggregateType("ORDER")
                .aggregateId(String.valueOf(orderId))
                .topic(paymentKafkaTopicsProperties.paymentEventTopic())
                .partitionKey(String.valueOf(orderId))
                .payload(payload)
                .build();
        paymentDomainEventPublisher.publish(event);
    }
}

package com.wearhouse.payment.kafka.publisher;

import com.wearhouse.payment.domain.payment.event.PaymentDomainEvent;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEventPublisher;
import com.wearhouse.payment.support.PaymentIdGenerator;
import com.wearhouse.payment.support.config.PaymentKafkaTopicsProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        PaymentAuthorizedPayload payload = new PaymentAuthorizedPayload(
                orderId,
                orderNo,
                paymentId,
                amount,
                paymentMethod,
                authorizedAt
        );
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
        PaymentAuthorizedFromWebhookPayload payload = new PaymentAuthorizedFromWebhookPayload(
                orderId,
                orderNo,
                paymentId,
                paymentKey,
                txHash,
                authorizedAt
        );
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
        PaymentFailedPayload payload = new PaymentFailedPayload(
                orderId,
                orderNo,
                paymentId,
                reasonCode,
                reasonMessage,
                failedAt
        );
        publish(orderId, PAYMENT_FAILED_EVENT, payload);
    }

    private void publish(Long orderId, String eventType, Object payload) {
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

    private record PaymentAuthorizedPayload(
            Long orderId,
            String orderNo,
            String paymentId,
            BigDecimal amount,
            String method,
            LocalDateTime authorizedAt
    ) {
    }

    private record PaymentAuthorizedFromWebhookPayload(
            Long orderId,
            String orderNo,
            String paymentId,
            String paymentKey,
            String txHash,
            LocalDateTime authorizedAt
    ) {
    }

    private record PaymentFailedPayload(
            Long orderId,
            String orderNo,
            String paymentId,
            String reasonCode,
            String reasonMessage,
            LocalDateTime failedAt
    ) {
    }
}

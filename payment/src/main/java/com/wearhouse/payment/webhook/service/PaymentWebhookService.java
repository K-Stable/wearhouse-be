package com.wearhouse.payment.webhook.service;

import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEvent;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEventPublisher;
import com.wearhouse.payment.domain.payment.model.PaymentStatus;
import com.wearhouse.payment.infra.jpa.repository.PaymentTransactionRepository;
import com.wearhouse.payment.support.PaymentIdGenerator;
import com.wearhouse.payment.webhook.dto.request.PayWebhookRequest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private final PaymentWebhookValidationService paymentWebhookValidationService;
    private final PaymentWebhookDedupService paymentWebhookDedupService;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentDomainEventPublisher paymentDomainEventPublisher;
    @Value("${wearhouse.kafka.payment-event-topic:wearhouse.payment.event.v1}")
    private String paymentEventTopic;

    @WriteTx
    public void handle(String timestamp, String signature, String rawBody) {
        try {
            PayWebhookRequest webhookRequest = paymentWebhookValidationService.validateAndRead(
                    timestamp,
                    signature,
                    rawBody
            );

            if (!paymentWebhookDedupService.registerIfAbsent(webhookRequest, rawBody)) {
                return;
            }

            if ("payment.authorized".equals(webhookRequest.eventType())) {
                handlePaymentAuthorized(webhookRequest);
                return;
            }
            if ("payment.failed".equals(webhookRequest.eventType())) {
                handlePaymentFailed(webhookRequest);
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "웹훅 처리 중 오류가 발생했습니다.", exception);
        }
    }

    private void handlePaymentAuthorized(PayWebhookRequest webhookRequest) {
        Optional<PaymentTransactionEntity> target = findTransaction(webhookRequest.payment());
        if (target.isEmpty()) {
            return;
        }

        PaymentTransactionEntity transaction = target.get();
        if (transaction.getStatus() == PaymentStatus.AUTHORIZED) {
            return;
        }

        transaction.bindStablepaySession(
                coalesce(webhookRequest.payment().paymentKey(), transaction.getPaymentKey()),
                transaction.getPaymentSessionId(),
                coalesce(webhookRequest.payment().merchantKey(), transaction.getMerchantKey()),
                transaction.getNonce(),
                transaction.getDeadline(),
                transaction.getPayloadHash(),
                coalesce(webhookRequest.payment().payerAddress(), transaction.getPayerAddress()),
                coalesce(webhookRequest.payment().tokenAddress(), transaction.getTokenAddress())
        );
        transaction.authorizeByWebhook(
                webhookRequest.payment().txHash(),
                webhookRequest.payment().commandId(),
                coalesce(webhookRequest.payment().commandStatus(), "authorized_confirmed"),
                parseOccurredAtOrNow(webhookRequest.occurredAt())
        );
        paymentTransactionRepository.save(transaction);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", transaction.getOrderId());
        payload.put("orderNo", transaction.getOrderNo());
        payload.put("paymentId", transaction.getPaymentId());
        payload.put("paymentKey", transaction.getPaymentKey());
        payload.put("txHash", transaction.getTxHash());
        payload.put("authorizedAt", transaction.getAuthorizedAt());

        paymentDomainEventPublisher.publish(PaymentDomainEvent.builder()
                .eventId(PaymentIdGenerator.newEventId())
                .eventType("PaymentAuthorized")
                .aggregateType("ORDER")
                .aggregateId(String.valueOf(transaction.getOrderId()))
                .topic(paymentEventTopic)
                .partitionKey(String.valueOf(transaction.getOrderId()))
                .payload(payload)
                .build());
    }

    private void handlePaymentFailed(PayWebhookRequest webhookRequest) {
        Optional<PaymentTransactionEntity> target = findTransaction(webhookRequest.payment());
        if (target.isEmpty()) {
            return;
        }

        PaymentTransactionEntity transaction = target.get();
        if (transaction.getStatus() == PaymentStatus.FAILED) {
            return;
        }

        String reasonCode = coalesce(webhookRequest.payment().reasonCode(), "PAYMENT_FAILED");
        transaction.failByWebhook(
                reasonCode,
                webhookRequest.payment().commandId(),
                coalesce(webhookRequest.payment().commandStatus(), "failed"),
                parseOccurredAtOrNow(webhookRequest.occurredAt())
        );
        paymentTransactionRepository.save(transaction);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", transaction.getOrderId());
        payload.put("orderNo", transaction.getOrderNo());
        payload.put("paymentId", transaction.getPaymentId());
        payload.put("reasonCode", reasonCode);
        payload.put("reasonMessage", coalesce(webhookRequest.payment().reasonMessage(), "결제 승인에 실패했습니다."));
        payload.put("failedAt", transaction.getFailedAt());

        paymentDomainEventPublisher.publish(PaymentDomainEvent.builder()
                .eventId(PaymentIdGenerator.newEventId())
                .eventType("PaymentFailed")
                .aggregateType("ORDER")
                .aggregateId(String.valueOf(transaction.getOrderId()))
                .topic(paymentEventTopic)
                .partitionKey(String.valueOf(transaction.getOrderId()))
                .payload(payload)
                .build());
    }

    private Optional<PaymentTransactionEntity> findTransaction(PayWebhookRequest.PaymentWebhookPayload payload) {
        if (payload == null) {
            return Optional.empty();
        }
        if (payload.paymentId() != null && !payload.paymentId().isBlank()) {
            Optional<PaymentTransactionEntity> byPaymentId = paymentTransactionRepository.findByPaymentId(payload.paymentId());
            if (byPaymentId.isPresent()) {
                return byPaymentId;
            }
        }
        if (payload.paymentKey() != null && !payload.paymentKey().isBlank()) {
            return paymentTransactionRepository.findByPaymentKey(payload.paymentKey());
        }
        return Optional.empty();
    }

    private LocalDateTime parseOccurredAt(String occurredAt) {
        if (occurredAt == null || occurredAt.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(occurredAt).toLocalDateTime();
    }

    private LocalDateTime parseOccurredAtOrNow(String occurredAt) {
        LocalDateTime parsed = parseOccurredAt(occurredAt);
        return parsed == null ? LocalDateTime.now() : parsed;
    }

    private String coalesce(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}

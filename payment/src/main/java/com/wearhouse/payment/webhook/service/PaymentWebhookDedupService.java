package com.wearhouse.payment.webhook.service;

import com.wearhouse.payment.domain.payment.entity.PaymentWebhookEventEntity;
import com.wearhouse.payment.infra.jpa.repository.PaymentWebhookRepository;
import com.wearhouse.payment.webhook.dto.request.PayWebhookRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentWebhookDedupService {

    private final PaymentWebhookRepository paymentWebhookRepository;

    public boolean registerIfAbsent(PayWebhookRequest webhookRequest, String rawBody) {
        try {
            paymentWebhookRepository.save(PaymentWebhookEventEntity.received(
                    webhookRequest.eventId(),
                    webhookRequest.eventType(),
                    parseOccurredAt(webhookRequest.occurredAt()),
                    LocalDateTime.now(),
                    rawBody,
                    sha256Hex(rawBody)
            ));
            return true;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    private LocalDateTime parseOccurredAt(String occurredAt) {
        if (occurredAt == null || occurredAt.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(occurredAt).toLocalDateTime();
    }

    private String sha256Hex(String rawBody) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawBody.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("웹훅 payload hash 생성에 실패했습니다.", exception);
        }
    }
}


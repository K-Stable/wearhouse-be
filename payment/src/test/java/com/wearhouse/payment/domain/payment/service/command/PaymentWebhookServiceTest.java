package com.wearhouse.payment.domain.payment.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEventPublisher;
import com.wearhouse.payment.domain.payment.model.PaymentStatus;
import com.wearhouse.payment.infra.jpa.repository.PaymentTransactionRepository;
import com.wearhouse.payment.infra.jpa.repository.PaymentWebhookRepository;
import com.wearhouse.payment.support.security.PayWebhookSignatureVerifier;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

    @Mock
    private PaymentWebhookRepository paymentWebhookRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private PaymentDomainEventPublisher paymentDomainEventPublisher;

    private PaymentWebhookService paymentWebhookService;

    @BeforeEach
    void setUp() {
        paymentWebhookService = new PaymentWebhookService(
                new ObjectMapper(),
                new PayWebhookSignatureVerifier("test-secret", 300),
                paymentWebhookRepository,
                paymentTransactionRepository,
                paymentDomainEventPublisher
        );
        ReflectionTestUtils.setField(paymentWebhookService, "paymentEventTopic", "wearhouse.payment.event.v1");
    }

    @Test
    void authorized_webhook은_멱등으로_한번만_처리된다() throws Exception {
        PaymentTransactionEntity transaction = PaymentTransactionEntity.pending(
                "PAY123",
                1L,
                "O202603190001",
                new BigDecimal("10000"),
                "STABLE",
                LocalDateTime.now().plusMinutes(10)
        );

        when(paymentTransactionRepository.findByPaymentId("PAY123")).thenReturn(Optional.of(transaction));
        doNothing()
                .doThrow(new DuplicateKeyException("duplicate"))
                .when(paymentWebhookRepository)
                .save(any());

        String timestamp = OffsetDateTime.now(ZoneOffset.UTC).toString();
        String rawBody = """
                {
                  "eventId": "evt_1",
                  "eventType": "payment.authorized",
                  "occurredAt": "2026-03-19T10:00:00Z",
                  "payment": {
                    "paymentId": "PAY123",
                    "paymentKey": "pay_1",
                    "merchantKey": "merchant_1",
                    "payerAddress": "0xpayer",
                    "tokenAddress": "0xtoken",
                    "txHash": "0xhash",
                    "commandId": "cmd_1",
                    "commandStatus": "authorized_confirmed"
                  }
                }
                """;
        String signature = "sha256=" + sign("test-secret", timestamp + "." + rawBody);

        paymentWebhookService.handle(timestamp, signature, rawBody);
        paymentWebhookService.handle(timestamp, signature, rawBody);

        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        verify(paymentTransactionRepository, times(1)).save(transaction);
        verify(paymentDomainEventPublisher, times(1)).publish(any());
    }

    private String sign(String secret, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}

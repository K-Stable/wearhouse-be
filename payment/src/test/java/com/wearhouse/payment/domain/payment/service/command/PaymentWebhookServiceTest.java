package com.wearhouse.payment.domain.payment.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.domain.payment.model.PaymentStatus;
import com.wearhouse.payment.infra.jpa.repository.PaymentTransactionRepository;
import com.wearhouse.payment.kafka.publisher.PaymentEventPublishService;
import com.wearhouse.payment.webhook.dto.request.PayWebhookRequest;
import com.wearhouse.payment.webhook.dto.request.PayWebhookRequest.PaymentWebhookPayload;
import com.wearhouse.payment.webhook.service.PaymentWebhookDedupService;
import com.wearhouse.payment.webhook.service.PaymentWebhookService;
import com.wearhouse.payment.webhook.service.PaymentWebhookValidationService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private PaymentWebhookValidationService paymentWebhookValidationService;
    @Mock
    private PaymentWebhookDedupService paymentWebhookDedupService;
    @Mock
    private PaymentEventPublishService paymentEventPublishService;

    private PaymentWebhookService paymentWebhookService;

    @BeforeEach
    void setUp() {
        paymentWebhookService = new PaymentWebhookService(
                paymentWebhookValidationService,
                paymentWebhookDedupService,
                paymentTransactionRepository,
                paymentEventPublishService
        );
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

        PayWebhookRequest webhookRequest = new PayWebhookRequest(
                "evt_1",
                "payment.authorized",
                "2026-03-19T10:00:00Z",
                new PaymentWebhookPayload(
                        "pay_1",
                        "PAY123",
                        null,
                        "merchant_1",
                        "0xpayer",
                        "0xtoken",
                        "10000",
                        "0xhash",
                        "cmd_1",
                        "authorized_confirmed",
                        null,
                        null
                )
        );

        String timestamp = "2026-03-19T10:00:10Z";
        String signature = "sha256=test-signature";
        String rawBody = "{\"eventId\":\"evt_1\"}";

        when(paymentTransactionRepository.findByPaymentId("PAY123")).thenReturn(Optional.of(transaction));
        when(paymentWebhookValidationService.validateAndRead(eq(timestamp), eq(signature), eq(rawBody)))
                .thenReturn(webhookRequest);
        when(paymentWebhookDedupService.registerIfAbsent(eq(webhookRequest), eq(rawBody)))
                .thenReturn(true)
                .thenReturn(false);

        paymentWebhookService.handle(timestamp, signature, rawBody);
        paymentWebhookService.handle(timestamp, signature, rawBody);

        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        verify(paymentTransactionRepository, times(1)).save(transaction);
        verify(paymentEventPublishService, times(1)).publishAuthorizedFromWebhook(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }
}

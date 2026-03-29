package com.wearhouse.payment.domain.payment.service.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.payment.domain.payment.event.PaymentDomainEventPublisher;
import com.wearhouse.payment.internal.service.PaymentCommandService;
import com.wearhouse.payment.kafka.dto.PaymentPrepareRequestedEvent;
import com.wearhouse.payment.infra.jpa.repository.PaymentInboxRepository;
import com.wearhouse.payment.support.monitoring.PaymentKafkaFlowMetrics;
import com.wearhouse.payment.transaction.service.PaymentTransactionCreateService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceStablepayTest {

    @Mock
    private PaymentInboxRepository paymentInboxRepository;
    @Mock
    private PaymentTransactionCreateService paymentTransactionCreateService;
    @Mock
    private PaymentDomainEventPublisher paymentDomainEventPublisher;
    @Mock
    private PaymentKafkaFlowMetrics paymentKafkaFlowMetrics;

    private PaymentCommandService paymentCommandService;

    @BeforeEach
    void setUp() {
        paymentCommandService = new PaymentCommandService(
                paymentInboxRepository,
                paymentTransactionCreateService,
                paymentDomainEventPublisher,
                paymentKafkaFlowMetrics
        );
        ReflectionTestUtils.setField(paymentCommandService, "paymentEventTopic", "wearhouse.payment.event.v1");
        ReflectionTestUtils.setField(paymentCommandService, "pendingTimeoutMinutes", 30);
        ReflectionTestUtils.setField(paymentCommandService, "failMethodsRaw", "FAIL");
        ReflectionTestUtils.setField(paymentCommandService, "timeoutMethodsRaw", "TIMEOUT");
        paymentCommandService.init();
    }

    @Test
    void stablepay_prepare는_pending만_생성하고_authorized_이벤트를_발행하지_않는다() {
        when(paymentInboxRepository.tryReceive(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(paymentTransactionCreateService.findByOrderId(1L)).thenReturn(Optional.empty());

        PaymentPrepareRequestedEvent payload = new PaymentPrepareRequestedEvent(
                1L,
                "O202603190001",
                new BigDecimal("10000"),
                "STABLE"
        );

        paymentCommandService.handlePaymentPrepareRequested("evt_1", "topic", "1", "{}", payload);

        verify(paymentTransactionCreateService).insertPending(
                anyString(),
                eq(1L),
                eq("O202603190001"),
                eq(new BigDecimal("10000")),
                eq("STABLE"),
                any()
        );
        verify(paymentDomainEventPublisher, never()).publish(any());
    }
}

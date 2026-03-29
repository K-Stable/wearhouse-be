package com.wearhouse.payment.domain.payment.service.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.payment.internal.service.PaymentInternalCommandService;
import com.wearhouse.payment.kafka.dto.PaymentPrepareRequestedEvent;
import com.wearhouse.payment.kafka.publisher.PaymentEventPublishService;
import com.wearhouse.payment.infra.jpa.repository.PaymentInboxRepository;
import com.wearhouse.payment.support.config.PaymentMockProperties;
import com.wearhouse.payment.support.monitoring.PaymentKafkaFlowMetrics;
import com.wearhouse.payment.transaction.service.PaymentTransactionCreateService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceStablepayTest {

    @Mock
    private PaymentInboxRepository paymentInboxRepository;
    @Mock
    private PaymentTransactionCreateService paymentTransactionCreateService;
    @Mock
    private PaymentKafkaFlowMetrics paymentKafkaFlowMetrics;
    @Mock
    private PaymentEventPublishService paymentEventPublishService;

    private PaymentInternalCommandService paymentInternalCommandService;

    @BeforeEach
    void setUp() {
        paymentInternalCommandService = new PaymentInternalCommandService(
                paymentInboxRepository,
                paymentTransactionCreateService,
                paymentEventPublishService,
                paymentKafkaFlowMetrics,
                new PaymentMockProperties(30, 10000L, 200, "FAIL", "TIMEOUT")
        );
        paymentInternalCommandService.init();
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

        paymentInternalCommandService.handlePaymentPrepareRequested("evt_1", "topic", "1", "{}", payload);

        verify(paymentTransactionCreateService).insertPending(
                anyString(),
                eq(1L),
                eq("O202603190001"),
                eq(new BigDecimal("10000")),
                eq("STABLE"),
                any()
        );
        verify(paymentEventPublishService, never()).publishAuthorized(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
        verify(paymentEventPublishService, never()).publishFailed(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }
}

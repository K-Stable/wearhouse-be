package com.wearhouse.payment.domain.payment.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.payment.domain.payment.dto.request.PaymentConfirmRequest;
import com.wearhouse.payment.domain.payment.dto.response.PaymentConfirmResponse;
import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEventPublisher;
import com.wearhouse.payment.infra.jpa.repository.PaymentInboxRepository;
import com.wearhouse.payment.infra.jpa.repository.PaymentTransactionRepository;
import com.wearhouse.payment.infra.pay.PayConfirmGateway;
import com.wearhouse.payment.support.monitoring.PaymentKafkaFlowMetrics;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceConfirmTest {

    @Mock
    private PaymentInboxRepository paymentInboxRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private PayConfirmGateway payConfirmGateway;
    @Mock
    private PaymentDomainEventPublisher paymentDomainEventPublisher;
    @Mock
    private PaymentKafkaFlowMetrics paymentKafkaFlowMetrics;

    private PaymentCommandService paymentCommandService;

    @BeforeEach
    void setUp() {
        paymentCommandService = new PaymentCommandService(
                paymentInboxRepository,
                paymentTransactionRepository,
                payConfirmGateway,
                paymentDomainEventPublisher,
                paymentKafkaFlowMetrics
        );
        ReflectionTestUtils.setField(paymentCommandService, "paymentEventTopic", "wearhouse.payment.event.v1");
        ReflectionTestUtils.setField(paymentCommandService, "pendingTimeoutMinutes", 30);
        ReflectionTestUtils.setField(paymentCommandService, "timeoutBatchSize", 100);
        ReflectionTestUtils.setField(paymentCommandService, "failMethodsRaw", "FAIL");
        ReflectionTestUtils.setField(paymentCommandService, "timeoutMethodsRaw", "TIMEOUT");
        ReflectionTestUtils.setField(paymentCommandService, "internalSharedSecret", "internal-secret");
        paymentCommandService.init();
    }

    @Test
    void confirm_성공시_authorized_반영과_이벤트를_발행한다() {
        PaymentTransactionEntity pending = PaymentTransactionEntity.pending(
                "pid_1",
                1L,
                "O202603190001",
                new BigDecimal("10000"),
                "STABLE",
                LocalDateTime.now().plusMinutes(30)
        );
        pending.bindStablepaySession("pay_key_1", null, null, null, null, null, null, null);

        when(paymentTransactionRepository.findByOrderId(1L)).thenReturn(Optional.of(pending));
        when(payConfirmGateway.confirm(any())).thenReturn(PayConfirmGateway.PayConfirmResult.authorized(
                "pid_1",
                "cmd_1",
                "authorized_confirmed",
                "0xtx"
        ));

        PaymentConfirmResponse response = paymentCommandService.confirmStablepayPayment(
                new PaymentConfirmRequest(1L, "O202603190001", "pay_key_1", new BigDecimal("10000")),
                "internal-secret"
        );

        assertThat(response.paymentStatus()).isEqualTo("AUTHORIZED");
        assertThat(pending.getStatus().name()).isEqualTo("AUTHORIZED");
        verify(paymentTransactionRepository).save(pending);
        verify(paymentDomainEventPublisher).publish(any());
    }

    @Test
    void confirm_금액불일치시_실패한다() {
        PaymentTransactionEntity pending = PaymentTransactionEntity.pending(
                "pid_1",
                1L,
                "O202603190001",
                new BigDecimal("10000"),
                "STABLE",
                LocalDateTime.now().plusMinutes(30)
        );
        when(paymentTransactionRepository.findByOrderId(1L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> paymentCommandService.confirmStablepayPayment(
                new PaymentConfirmRequest(1L, "O202603190001", "pay_key_1", new BigDecimal("9999")),
                "internal-secret"
        )).isInstanceOf(IllegalArgumentException.class);

        verify(payConfirmGateway, never()).confirm(any());
        verify(paymentDomainEventPublisher, never()).publish(any());
        verify(paymentTransactionRepository, never()).save(any());
    }
}

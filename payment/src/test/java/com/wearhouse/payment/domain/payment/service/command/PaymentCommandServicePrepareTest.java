package com.wearhouse.payment.domain.payment.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.payment.domain.payment.dto.request.WalletPrepareRequest;
import com.wearhouse.payment.domain.payment.dto.response.WalletPrepareResponse;
import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEventPublisher;
import com.wearhouse.payment.infra.jpa.repository.PaymentInboxRepository;
import com.wearhouse.payment.infra.jpa.repository.PaymentTransactionRepository;
import com.wearhouse.payment.infra.pay.WalletServerGateway;
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
class PaymentCommandServicePrepareTest {

    @Mock
    private PaymentInboxRepository paymentInboxRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private WalletServerGateway walletServerGateway;
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
                walletServerGateway,
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
    void stable_prepare는_wallet_세션을_반환한다() {
        PaymentTransactionEntity pending = PaymentTransactionEntity.pending(
                "pid_1",
                1L,
                "O202603230001",
                new BigDecimal("10000"),
                "STABLE",
                LocalDateTime.now().plusMinutes(30)
        );
        when(paymentTransactionRepository.findByOrderId(1L)).thenReturn(Optional.of(pending));
        when(walletServerGateway.walletPrepare(any(), any())).thenReturn(
                new WalletServerGateway.WalletPrepareResult(
                        "cs_1",
                        "https://wallet.example/checkout/cs_1",
                        "wallet://checkout/cs_1",
                        null,
                        "READY"
                )
        );

        WalletPrepareResponse response = paymentCommandService.walletPrepare(
                new WalletPrepareRequest(
                        1L,
                        "O202603230001",
                        "customer-1",
                        "order-name",
                        new BigDecimal("10000"),
                        "https://shop.example/success",
                        "https://shop.example/fail",
                        "idem-1"
                ),
                "internal-secret"
        );

        assertThat(response.checkoutSessionId()).isEqualTo("cs_1");
        assertThat(response.checkoutUrl()).isEqualTo("https://wallet.example/checkout/cs_1");
        assertThat(response.appLaunchUrl()).isEqualTo("wallet://checkout/cs_1");
        verify(paymentTransactionRepository).save(pending);
    }
}

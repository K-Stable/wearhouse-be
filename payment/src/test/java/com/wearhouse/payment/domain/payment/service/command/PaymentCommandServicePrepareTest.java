package com.wearhouse.payment.domain.payment.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.payment.internal.dto.request.WalletPrepareRequest;
import com.wearhouse.payment.internal.dto.response.WalletPrepareResponse;
import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.internal.mapper.PaymentInternalResponseMapper;
import com.wearhouse.payment.internal.service.PaymentInternalPrepareService;
import com.wearhouse.payment.kafka.publisher.PaymentEventPublishService;
import com.wearhouse.payment.support.config.PaymentMockProperties;
import com.wearhouse.payment.support.config.PaymentOrderInternalProperties;
import com.wearhouse.payment.stablepay.client.WalletServerGateway;
import com.wearhouse.payment.transaction.service.PaymentTransactionCreateService;
import com.wearhouse.payment.transaction.service.PaymentTransactionUpdateService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServicePrepareTest {

    @Mock
    private PaymentTransactionCreateService paymentTransactionCreateService;
    @Mock
    private PaymentTransactionUpdateService paymentTransactionUpdateService;
    @Mock
    private WalletServerGateway walletServerGateway;
    @Mock
    private PaymentEventPublishService paymentEventPublishService;

    private PaymentInternalPrepareService paymentInternalPrepareService;

    @BeforeEach
    void setUp() {
        paymentInternalPrepareService = new PaymentInternalPrepareService(
                paymentTransactionCreateService,
                paymentTransactionUpdateService,
                walletServerGateway,
                paymentEventPublishService,
                new PaymentMockProperties(30, 10000L, 200, "FAIL", "TIMEOUT"),
                new PaymentOrderInternalProperties("internal-secret"),
                new PaymentInternalResponseMapper()
        );
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
        when(paymentTransactionCreateService.getOrCreateStablePending(1L, "O202603230001", new BigDecimal("10000"), 30))
                .thenReturn(pending);
        when(walletServerGateway.walletPrepare(any(), any())).thenReturn(
                new WalletServerGateway.WalletPrepareResult(
                        "cs_1",
                        "https://wallet.example/checkout/cs_1",
                        "wallet://checkout/cs_1",
                        null,
                        "READY"
                )
        );

        WalletPrepareResponse response = paymentInternalPrepareService.prepare(
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
        verify(paymentTransactionUpdateService).save(pending);
    }
}

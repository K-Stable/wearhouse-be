package com.wearhouse.payment.scheduler;

import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.payment.transaction.service.PaymentPendingTimeoutService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentTimeoutScheduler {

    private final PaymentPendingTimeoutService paymentPendingTimeoutService;

    public PaymentTimeoutScheduler(PaymentPendingTimeoutService paymentPendingTimeoutService) {
        this.paymentPendingTimeoutService = paymentPendingTimeoutService;
    }

    @WriteTx
    @Scheduled(fixedDelayString = "${wearhouse.payment.mock.timeout-check-interval-ms:10000}")
    public void failExpiredPendingPayments() {
        paymentPendingTimeoutService.failExpiredPendingPayments();
    }
}

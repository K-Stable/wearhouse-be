package com.wearhouse.payment.domain.payment.service.command;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentTimeoutScheduler {

    private final PaymentCommandService paymentCommandService;

    public PaymentTimeoutScheduler(PaymentCommandService paymentCommandService) {
        this.paymentCommandService = paymentCommandService;
    }

    @Transactional
    @Scheduled(fixedDelayString = "${wearhouse.payment.mock.timeout-check-interval-ms:10000}")
    public void failExpiredPendingPayments() {
        paymentCommandService.failExpiredPendingPayments();
    }
}

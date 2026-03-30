package com.wearhouse.payment.domain.payment.service.command;

import com.wearhouse.common.global.transactional.WriteTx;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wearhouse.outbox.relay.enabled", havingValue = "true")
@RequiredArgsConstructor
public class PaymentOutboxRelayScheduler {

    private final PaymentOutboxRepublishBatchService paymentOutboxRepublishBatchService;

    @WriteTx
    @Scheduled(fixedDelayString = "${wearhouse.outbox.republish-interval-ms:500}")
    public void publishOutboxEvents() {
        paymentOutboxRepublishBatchService.publishOutboxEvents();
    }
}

package com.wearhouse.payment.domain.payment.service.command;

import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.common.support.config.OutboxProperties;
import com.wearhouse.payment.infra.jpa.repository.PaymentOutboxRepository;
import com.wearhouse.payment.infra.jpa.repository.PaymentOutboxRepository.OutboxCandidate;
import com.wearhouse.payment.infra.kafka.producer.PaymentKafkaProducer;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentOutboxRepublishScheduler {

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final PaymentKafkaProducer paymentKafkaProducer;
    private final OutboxProperties outboxProperties;

    @WriteTx
    @Scheduled(fixedDelayString = "${wearhouse.outbox.republish-interval-ms:60000}")
    public void republish() {
        LocalDateTime cutoffAt = LocalDateTime.now().minusMinutes(outboxProperties.staleMinutes());
        List<OutboxCandidate> candidates = paymentOutboxRepository.lockRepublishCandidates(
                cutoffAt,
                outboxProperties.republishBatchSize()
        );
        for (OutboxCandidate candidate : candidates) {
            paymentKafkaProducer.send(
                    candidate.getEventId(),
                    candidate.getEventType(),
                    candidate.getTopic(),
                    candidate.getPartitionKey(),
                    candidate.getPayload(),
                    candidate.getRetryCount(),
                    "republish"
            );
        }
    }
}

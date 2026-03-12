package com.wearhouse.order.domain.service.command;

import com.wearhouse.common.support.config.OutboxProperties;
import com.wearhouse.order.infra.kafka.producer.OrderKafkaProducer;
import com.wearhouse.order.infra.jpa.repository.OrderOutboxRepository;
import com.wearhouse.order.infra.jpa.repository.OrderOutboxRepository.OutboxCandidate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.wearhouse.common.global.transactional.WriteTx;

@Component
@RequiredArgsConstructor
public class OrderOutboxRepublishScheduler {

    private final OrderOutboxRepository orderOutboxRepository;
    private final OrderKafkaProducer orderKafkaProducer;
    private final OutboxProperties outboxProperties;

    @WriteTx
    @Scheduled(fixedDelayString = "${wearhouse.outbox.republish-interval-ms:60000}")
    public void republish() {
        LocalDateTime cutoffAt = LocalDateTime.now().minusMinutes(outboxProperties.staleMinutes());
        List<OutboxCandidate> candidates = orderOutboxRepository.lockRepublishCandidates(
                cutoffAt,
                outboxProperties.republishBatchSize()
        );
        for (OutboxCandidate candidate : candidates) {
            orderKafkaProducer.send(
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

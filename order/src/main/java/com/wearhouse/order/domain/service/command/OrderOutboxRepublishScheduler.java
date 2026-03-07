package com.wearhouse.order.domain.service.command;

import com.wearhouse.order.infra.kafka.service.OrderOutboxKafkaPublishService;
import com.wearhouse.order.infra.jpa.repository.OrderOutboxRepository;
import com.wearhouse.order.infra.jpa.repository.OrderOutboxRepository.OutboxCandidate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderOutboxRepublishScheduler {

    private final OrderOutboxRepository orderOutboxRepository;
    private final OrderOutboxKafkaPublishService orderOutboxKafkaPublishService;
    private final int staleMinutes;
    private final int batchSize;

    public OrderOutboxRepublishScheduler(
            OrderOutboxRepository orderOutboxRepository,
            OrderOutboxKafkaPublishService orderOutboxKafkaPublishService,
            @Value("${wearhouse.outbox.stale-minutes:10}") int staleMinutes,
            @Value("${wearhouse.outbox.republish-batch-size:100}") int batchSize
    ) {
        this.orderOutboxRepository = orderOutboxRepository;
        this.orderOutboxKafkaPublishService = orderOutboxKafkaPublishService;
        this.staleMinutes = staleMinutes;
        this.batchSize = batchSize;
    }

    @Transactional
    @Scheduled(fixedDelayString = "${wearhouse.outbox.republish-interval-ms:60000}")
    public void republish() {
        LocalDateTime cutoffAt = LocalDateTime.now().minusMinutes(staleMinutes);
        List<OutboxCandidate> candidates = orderOutboxRepository.lockRepublishCandidates(cutoffAt, batchSize);
        for (OutboxCandidate candidate : candidates) {
            orderOutboxKafkaPublishService.send(
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

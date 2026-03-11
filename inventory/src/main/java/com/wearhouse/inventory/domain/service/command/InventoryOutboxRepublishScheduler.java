package com.wearhouse.inventory.domain.service.command;

import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.inventory.domain.repository.InventoryOutboxRepository;
import com.wearhouse.inventory.domain.repository.InventoryOutboxRepository.OutboxCandidate;
import com.wearhouse.inventory.infra.kafka.service.InventoryKafkaPublishService;
import com.wearhouse.inventory.support.config.InventoryOutboxProperties;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryOutboxRepublishScheduler {

    private final InventoryOutboxRepository inventoryOutboxRepository;
    private final InventoryKafkaPublishService inventoryKafkaPublishService;
    private final InventoryOutboxProperties outboxProperties;

    @WriteTx
    @Scheduled(fixedDelayString = "${wearhouse.outbox.republish-interval-ms:60000}")
    public void republish() {
        LocalDateTime cutoffAt = LocalDateTime.now().minusMinutes(outboxProperties.staleMinutes());
        List<OutboxCandidate> candidates = inventoryOutboxRepository.lockRepublishCandidates(
                cutoffAt,
                outboxProperties.republishBatchSize()
        );
        for (OutboxCandidate candidate : candidates) {
            inventoryKafkaPublishService.send(
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

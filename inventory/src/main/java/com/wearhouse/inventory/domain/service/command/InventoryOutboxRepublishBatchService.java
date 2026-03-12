package com.wearhouse.inventory.domain.service.command;

import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.common.support.config.OutboxProperties;
import com.wearhouse.inventory.domain.repository.InventoryOutboxRepository;
import com.wearhouse.inventory.domain.repository.InventoryOutboxRepository.OutboxCandidate;
import com.wearhouse.inventory.infra.kafka.producer.InventoryKafkaProducer;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryOutboxRepublishBatchService {

    private final InventoryOutboxRepository inventoryOutboxRepository;
    private final InventoryKafkaProducer inventoryKafkaProducer;
    private final OutboxProperties outboxProperties;

    @WriteTx
    public int republishFailedEvents() {
        LocalDateTime cutoffAt = LocalDateTime.now().minusMinutes(outboxProperties.staleMinutes());
        List<OutboxCandidate> candidates = inventoryOutboxRepository.lockRepublishCandidates(
                cutoffAt,
                outboxProperties.republishBatchSize()
        );
        for (OutboxCandidate candidate : candidates) {
            inventoryKafkaProducer.send(
                    candidate.getEventId(),
                    candidate.getEventType(),
                    candidate.getTopic(),
                    candidate.getPartitionKey(),
                    candidate.getPayload(),
                    "batch_republish"
            );
        }
        return candidates.size();
    }
}

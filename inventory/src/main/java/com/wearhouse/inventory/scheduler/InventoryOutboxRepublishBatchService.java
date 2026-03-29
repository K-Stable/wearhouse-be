package com.wearhouse.inventory.scheduler;

import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.common.support.config.OutboxProperties;
import com.wearhouse.inventory.domain.repository.InventoryOutboxRepository;
import com.wearhouse.inventory.domain.repository.InventoryOutboxRepository.OutboxCandidate;
import com.wearhouse.inventory.infra.kafka.producer.InventoryKafkaProducer;
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
    public int publishOutboxEvents() {
        List<OutboxCandidate> candidates = inventoryOutboxRepository.lockRepublishCandidates(outboxProperties.republishBatchSize());
        for (OutboxCandidate candidate : candidates) {
            try {
                inventoryKafkaProducer.send(
                        candidate.getTopic(),
                        candidate.getPartitionKey(),
                        candidate.getPayload()
                );
                inventoryOutboxRepository.markSuccess(candidate.getEventId());
            } catch (Exception exception) {
                inventoryOutboxRepository.markFail(candidate.getEventId(), "KAFKA_SEND_ERROR", exception.getMessage());
            }
        }
        return candidates.size();
    }
}

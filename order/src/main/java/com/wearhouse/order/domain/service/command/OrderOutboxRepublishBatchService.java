package com.wearhouse.order.domain.service.command;

import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.common.support.config.OutboxProperties;
import com.wearhouse.order.infra.jpa.repository.OrderOutboxRepository;
import com.wearhouse.order.infra.jpa.repository.OrderOutboxRepository.OutboxCandidate;
import com.wearhouse.order.infra.kafka.producer.OrderKafkaProducer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderOutboxRepublishBatchService {

    private final OrderOutboxRepository orderOutboxRepository;
    private final OrderKafkaProducer orderKafkaProducer;
    private final OutboxProperties outboxProperties;

    @WriteTx
    public int publishOutboxEvents() {
        List<OutboxCandidate> candidates = orderOutboxRepository.lockRepublishCandidates(outboxProperties.republishBatchSize());
        for (OutboxCandidate candidate : candidates) {
            try {
                orderKafkaProducer.send(
                        candidate.getTopic(),
                        candidate.getPartitionKey(),
                        candidate.getPayload()
                );
                orderOutboxRepository.markSuccess(candidate.getEventId());
            } catch (Exception exception) {
                orderOutboxRepository.markFail(candidate.getEventId(), "KAFKA_SEND_ERROR", exception.getMessage());
            }
        }
        return candidates.size();
    }
}

package com.wearhouse.payment.domain.payment.service.command;

import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.common.support.config.OutboxProperties;
import com.wearhouse.payment.kafka.publisher.PaymentKafkaProducer;
import com.wearhouse.payment.infra.jpa.repository.PaymentOutboxRepository;
import com.wearhouse.payment.infra.jpa.repository.PaymentOutboxRepository.OutboxCandidate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentOutboxRepublishBatchService {

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final PaymentKafkaProducer paymentKafkaProducer;
    private final OutboxProperties outboxProperties;

    @WriteTx
    public int publishOutboxEvents() {
        List<OutboxCandidate> candidates = paymentOutboxRepository.lockRepublishCandidates(outboxProperties.republishBatchSize());
        for (OutboxCandidate candidate : candidates) {
            paymentKafkaProducer.send(
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

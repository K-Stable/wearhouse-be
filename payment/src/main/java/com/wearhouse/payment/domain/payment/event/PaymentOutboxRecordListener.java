package com.wearhouse.payment.domain.payment.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.payment.infra.jpa.repository.PaymentOutboxRepository;
import com.wearhouse.payment.support.monitoring.PaymentKafkaFlowMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaymentOutboxRecordListener {

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final ObjectMapper objectMapper;
    private final PaymentKafkaFlowMetrics paymentKafkaFlowMetrics;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void record(PaymentDomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event.toEnvelope());
            paymentOutboxRepository.saveReady(
                    event.getEventId(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getEventType(),
                    event.getTopic(),
                    event.getPartitionKey(),
                    payload
            );
            paymentKafkaFlowMetrics.incrementOutboxRecorded(event.getEventType(), event.getTopic());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Payment Outbox 이벤트 직렬화에 실패했습니다.", exception);
        }
    }
}


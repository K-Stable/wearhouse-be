package com.wearhouse.order.domain.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.order.infra.jpa.repository.OrderOutboxRepository;
import com.wearhouse.order.support.monitoring.OrderKafkaFlowMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderOutboxRecordListener {

    private final OrderOutboxRepository orderOutboxRepository;
    private final ObjectMapper objectMapper;
    private final OrderKafkaFlowMetrics orderKafkaFlowMetrics;


    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void recordMessageHandler(OrderDomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event.toEnvelope());
            orderOutboxRepository.saveReady(
                    event.getEventId(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getEventType(),
                    event.getTopic(),
                    event.getPartitionKey(),
                    payload
            );
            orderKafkaFlowMetrics.incrementOutboxRecorded(event.getEventType(), event.getTopic());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("주문 Outbox 이벤트 직렬화에 실패했습니다.", exception);
        }
    }
}

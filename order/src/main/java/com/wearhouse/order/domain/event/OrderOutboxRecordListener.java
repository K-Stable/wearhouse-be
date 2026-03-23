package com.wearhouse.order.domain.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.order.infra.jpa.repository.OrderOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderOutboxRecordListener {

    private final OrderOutboxRepository orderOutboxRepository;
    private final ObjectMapper objectMapper;


    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void recordMessageHandler(OrderDomainEvent event) {
        try {
            // 도메인 트랜잭션 안에서 Outbox를 먼저 기록해 유실 가능성을 줄인다.
            String payload = objectMapper.writeValueAsString(event.toEnvelope());
            orderOutboxRepository.saveReady(
                    event.getEventId(),
                    event.getEventType(),
                    event.getTopic(),
                    event.getPartitionKey(),
                    payload
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("주문 Outbox 이벤트 직렬화에 실패했습니다.", exception);
        }
    }
}

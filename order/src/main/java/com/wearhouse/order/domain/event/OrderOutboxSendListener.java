package com.wearhouse.order.domain.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.order.infra.jpa.repository.OrderOutboxRepository;
import com.wearhouse.order.infra.kafka.producer.OrderKafkaProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderOutboxSendListener {

    private final ObjectMapper objectMapper;
    private final OrderOutboxRepository orderOutboxRepository;
    private final OrderKafkaProducer orderKafkaProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendMessageHandler(OrderDomainEvent event) {
        try {
            // DB 커밋 이후에만 Kafka로 전송한다. (Outbox + AFTER_COMMIT)
            String payload = objectMapper.writeValueAsString(event.toEnvelope());
            orderKafkaProducer.send(
                    event.getTopic(),
                    event.getPartitionKey(),
                    payload
            );
            // 전송 성공 시 Outbox 상태를 SUCCESS로 마킹한다.
            orderOutboxRepository.markSuccess(event.getEventId());
        } catch (JsonProcessingException exception) {
            orderOutboxRepository.markFail(event.getEventId(), "SERIALIZE_ERROR", exception.getMessage());
            throw new IllegalStateException("주문 Outbox 이벤트 직렬화에 실패했습니다.", exception);
        } catch (Exception exception) {
            orderOutboxRepository.markFail(event.getEventId(), "KAFKA_SEND_ERROR", exception.getMessage());
            throw new IllegalStateException("주문 Outbox 이벤트 전송에 실패했습니다.", exception);
        }
    }
}

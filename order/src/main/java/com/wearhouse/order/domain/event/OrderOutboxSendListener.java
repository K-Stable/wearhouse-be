package com.wearhouse.order.domain.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.order.infra.jpa.repository.OrderOutboxRepository;
import com.wearhouse.order.kafka.publisher.OrderKafkaProducer;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.support.SendResult;
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
            // DB 커밋 이후 Kafka 전송은 비동기로 처리한다.
            String payload = objectMapper.writeValueAsString(event.toEnvelope());
            CompletableFuture<SendResult<String, String>> sendFuture = orderKafkaProducer.send(
                    event.getTopic(),
                    event.getPartitionKey(),
                    payload
            );
            sendFuture.whenComplete((result, throwable) -> {
                if (throwable == null) {
                    orderOutboxRepository.markSuccess(event.getEventId());
                    return;
                }
                orderOutboxRepository.markFail(
                        event.getEventId(),
                        "KAFKA_SEND_ERROR",
                        throwable.getMessage()
                );
            });
        } catch (JsonProcessingException exception) {
            orderOutboxRepository.markFail(event.getEventId(), "SERIALIZE_ERROR", exception.getMessage());
        } catch (Exception exception) {
            orderOutboxRepository.markFail(event.getEventId(), "KAFKA_SEND_ERROR", exception.getMessage());
        }
    }
}

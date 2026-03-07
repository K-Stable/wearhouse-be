package com.wearhouse.payment.domain.payment.event;

import com.wearhouse.payment.infra.kafka.service.PaymentKafkaPublishService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PaymentDomainEventPublishListener {

    private final PaymentKafkaPublishService paymentKafkaPublishService;

    public PaymentDomainEventPublishListener(PaymentKafkaPublishService paymentKafkaPublishService) {
        this.paymentKafkaPublishService = paymentKafkaPublishService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(PaymentDomainEvent event) {
        paymentKafkaPublishService.send(event);
    }
}

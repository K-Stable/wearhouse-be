package com.wearhouse.payment.domain.payment.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class PaymentDomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public PaymentDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publish(PaymentDomainEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}

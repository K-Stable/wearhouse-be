package com.wearhouse.order.domain.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class OrderDomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public OrderDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publish(OrderDomainEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}

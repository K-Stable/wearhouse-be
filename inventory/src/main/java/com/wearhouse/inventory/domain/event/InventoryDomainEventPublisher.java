package com.wearhouse.inventory.domain.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class InventoryDomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public InventoryDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publish(InventoryDomainEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}

package com.wearhouse.common.support.event;

public interface ExternalEventMessageListener<T> {

    void sendMessageHandler(T event);
}

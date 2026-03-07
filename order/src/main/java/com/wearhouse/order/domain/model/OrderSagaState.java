package com.wearhouse.order.domain.model;

public enum OrderSagaState {
    STARTED,
    WAITING_INVENTORY,
    RESERVE_FAILED,
    WAITING_PAYMENT_PREPARE,
    WAITING_PAYMENT_RESULT,
    PAID,
    CONFIRMED,
    COMPENSATING,
    CANCELLED,
    FAILED
}

package com.wearhouse.order.domain.model;

public enum OrderStatus {
    PENDING_RESERVE,
    RESERVE_FAILED,
    RESERVED,
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    PAID,
    CONFIRMED,
    DELIVERED,
    PURCHASE_CONFIRMED,
    CANCELLED,
    REFUND_PENDING
}

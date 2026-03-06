package com.wearhouse.order.domain.order.model;

public enum OrderOutboxStatus {
    READY,
    SEND_SUCCESS,
    SEND_FAIL,
    DEAD
}

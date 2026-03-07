package com.wearhouse.order.domain.model;

public enum OrderOutboxStatus {
    READY,
    SEND_SUCCESS,
    SEND_FAIL,
    DEAD
}

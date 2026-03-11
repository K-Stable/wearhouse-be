package com.wearhouse.payment.domain.payment.model;

public enum PaymentOutboxStatus {
    READY,
    SEND_SUCCESS,
    SEND_FAIL,
    DEAD
}


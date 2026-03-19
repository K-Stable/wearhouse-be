package com.wearhouse.order.domain.entity;

import com.wearhouse.order.domain.model.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class OrderInfo {

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "recipient_phone")
    private String recipientPhone;

    @Column(name = "zip_code")
    private String zipCode;

    @Column(name = "address1")
    private String address1;

    @Column(name = "address2")
    private String address2;

    @Column(name = "delivery_request")
    private String deliveryRequest;

    protected OrderInfo() {
    }

    private OrderInfo(
            PaymentMethod paymentMethod,
            String recipientName,
            String recipientPhone,
            String zipCode,
            String address1,
            String address2,
            String deliveryRequest
    ) {
        this.paymentMethod = paymentMethod;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
        this.deliveryRequest = deliveryRequest;
    }

    public static OrderInfo of(
            PaymentMethod paymentMethod,
            String recipientName,
            String recipientPhone,
            String zipCode,
            String address1,
            String address2,
            String deliveryRequest
    ) {
        return new OrderInfo(paymentMethod, recipientName, recipientPhone, zipCode, address1, address2, deliveryRequest);
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public String getZipCode() {
        return zipCode;
    }

    public String getAddress1() {
        return address1;
    }

    public String getAddress2() {
        return address2;
    }

    public String getDeliveryRequest() {
        return deliveryRequest;
    }
}

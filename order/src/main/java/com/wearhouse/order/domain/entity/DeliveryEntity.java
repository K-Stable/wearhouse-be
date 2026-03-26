package com.wearhouse.order.domain.entity;

import com.wearhouse.order.domain.model.DeliveryStatus;
import com.wearhouse.order.infra.jpa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "delivery")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "courier_code", nullable = false, length = 80)
    private String courierCode;

    @Column(name = "invoice_no", nullable = false, length = 120)
    private String invoiceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeliveryStatus status;

    private DeliveryEntity(
            OrderEntity order,
            String courierCode,
            String invoiceNo,
            DeliveryStatus status
    ) {
        this.order = order;
        this.courierCode = courierCode;
        this.invoiceNo = invoiceNo;
        this.status = status;
    }

    public static DeliveryEntity create(
            OrderEntity order,
            String courierCode,
            String invoiceNo,
            DeliveryStatus status
    ) {
        return new DeliveryEntity(order, courierCode, invoiceNo, status);
    }

    public void updateShipment(String courierCode, String invoiceNo) {
        this.courierCode = courierCode;
        this.invoiceNo = invoiceNo;
    }

    public void updateStatus(DeliveryStatus status) {
        this.status = status;
    }
}

package com.wearhouse.inventory.domain.entity;

import com.wearhouse.inventory.domain.model.InventoryReservationStatus;
import com.wearhouse.inventory.infra.jpa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "inventory_reservation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryReservationEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false, unique = true, length = 40)
    private String reservationId;

    @Column(name = "source_event_id", nullable = false, length = 26)
    private String sourceEventId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_no", length = 40)
    private String orderNo;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InventoryReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Builder
    private InventoryReservationEntity(
            String reservationId,
            String sourceEventId,
            Long orderId,
            String orderNo,
            Long skuId,
            Integer quantity,
            LocalDateTime expiresAt
    ) {
        this.reservationId = reservationId;
        this.sourceEventId = sourceEventId;
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.skuId = skuId;
        this.quantity = quantity;
        this.status = InventoryReservationStatus.RESERVED;
        this.expiresAt = expiresAt;
    }

    public static InventoryReservationEntity reserved(
            String reservationId,
            String sourceEventId,
            Long orderId,
            String orderNo,
            Long skuId,
            Integer quantity,
            LocalDateTime expiresAt
    ) {
        return new InventoryReservationEntity(
                reservationId,
                sourceEventId,
                orderId,
                orderNo,
                skuId,
                quantity,
                expiresAt
        );
    }

    public void release(LocalDateTime releasedAt) {
        if (this.status == InventoryReservationStatus.RELEASED) {
            return;
        }
        this.status = InventoryReservationStatus.RELEASED;
        this.releasedAt = releasedAt;
    }

    public void confirm(LocalDateTime confirmedAt) {
        if (this.status == InventoryReservationStatus.CONFIRMED) {
            return;
        }
        this.status = InventoryReservationStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
    }

    public Long getId() {
        return id;
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getSourceEventId() {
        return sourceEventId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getSkuId() {
        return skuId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public InventoryReservationStatus getStatus() {
        return status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getReleasedAt() {
        return releasedAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }
}

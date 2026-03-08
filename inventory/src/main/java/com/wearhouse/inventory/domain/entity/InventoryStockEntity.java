package com.wearhouse.inventory.domain.entity;

import com.wearhouse.inventory.infra.jpa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "inventory_stock")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryStockEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku_id", nullable = false, unique = true)
    private Long skuId;

    @Column(name = "available_qty", nullable = false)
    private Integer availableQty;

    @Column(name = "reserved_qty", nullable = false)
    private Integer reservedQty;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Builder
    private InventoryStockEntity(Long skuId, Integer availableQty) {
        this.skuId = skuId;
        this.availableQty = availableQty;
        this.reservedQty = 0;
    }

    public static InventoryStockEntity create(Long skuId, Integer availableQty) {
        return new InventoryStockEntity(skuId, availableQty);
    }

    public boolean canReserve(int quantity) {
        return quantity > 0 && availableQty >= quantity;
    }

    public void reserve(int quantity) {
        if (!canReserve(quantity)) {
            throw new IllegalStateException("재고가 부족하여 예약할 수 없습니다.");
        }
        this.availableQty = this.availableQty - quantity;
        this.reservedQty = this.reservedQty + quantity;
    }

    public void release(int quantity) {
        if (quantity <= 0) {
            return;
        }
        this.availableQty = this.availableQty + quantity;
        this.reservedQty = Math.max(0, this.reservedQty - quantity);
    }

    public void confirm(int quantity) {
        if (quantity <= 0) {
            return;
        }
        this.reservedQty = Math.max(0, this.reservedQty - quantity);
    }

    public void setAvailableQty(int availableQty) {
        this.availableQty = availableQty;
    }

    public Long getId() {
        return id;
    }

    public Long getSkuId() {
        return skuId;
    }

    public Integer getAvailableQty() {
        return availableQty;
    }

    public Integer getReservedQty() {
        return reservedQty;
    }

    public Long getVersion() {
        return version;
    }
}

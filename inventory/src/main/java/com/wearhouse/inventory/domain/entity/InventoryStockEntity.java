package com.wearhouse.inventory.domain.entity;

import com.wearhouse.inventory.domain.model.InventoryProductStatus;
import com.wearhouse.inventory.infra.jpa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.AccessLevel;
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

    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", length = 150)
    private String productName;

    @Column(name = "product_price", precision = 15, scale = 2)
    private BigDecimal productPrice;

    @Column(name = "product_category", length = 60)
    private String productCategory;

    @Column(name = "product_status", nullable = false, length = 20)
    private String productStatus;

    @Column(name = "option_size", length = 60)
    private String optionSize;

    @Column(name = "option_color", length = 60)
    private String optionColor;

    @Column(name = "main_image_url", length = 500)
    private String mainImageUrl;

    @Column(name = "available_qty", nullable = false)
    private Integer availableQty;

    @Column(name = "reserved_qty", nullable = false)
    private Integer reservedQty;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private InventoryStockEntity(
            Long skuId,
            Integer availableQty,
            Long sellerId,
            Long productId,
            String productName,
            BigDecimal productPrice,
            String productCategory,
            String productStatus,
            String optionSize,
            String optionColor,
            String mainImageUrl
    ) {
        this.skuId = skuId;
        this.availableQty = Math.max(availableQty == null ? 0 : availableQty, 0);
        this.reservedQty = 0;
        this.sellerId = sellerId;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.productCategory = productCategory;
        this.productStatus = productStatus;
        this.optionSize = optionSize;
        this.optionColor = optionColor;
        this.mainImageUrl = mainImageUrl;
        syncProductStatusByAvailableQty();
    }

    public static InventoryStockEntity of(
            Long skuId,
            Integer availableQty,
            Long sellerId,
            Long productId,
            String productName,
            BigDecimal productPrice,
            String productCategory,
            String productStatus,
            String optionSize,
            String optionColor,
            String mainImageUrl
    ) {
        return new InventoryStockEntity(
                skuId,
                availableQty,
                sellerId,
                productId,
                productName,
                productPrice,
                productCategory,
                productStatus,
                optionSize,
                optionColor,
                mainImageUrl
        );
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
        syncProductStatusByAvailableQty();
    }

    public void release(int quantity) {
        if (quantity <= 0) {
            return;
        }
        this.availableQty = this.availableQty + quantity;
        this.reservedQty = Math.max(0, this.reservedQty - quantity);
        syncProductStatusByAvailableQty();
    }

    public void confirm(int quantity) {
        if (quantity <= 0) {
            return;
        }
        this.reservedQty = Math.max(0, this.reservedQty - quantity);
        syncProductStatusByAvailableQty();
    }

    public void setAvailableQty(int availableQty) {
        this.availableQty = Math.max(availableQty, 0);
        syncProductStatusByAvailableQty();
    }

    public void setProductStatus(String productStatus) {
        this.productStatus = productStatus;
        syncProductStatusByAvailableQty();
    }

    public void updateSnapshot(
            Long sellerId,
            Long productId,
            String productName,
            BigDecimal productPrice,
            String productCategory,
            String productStatus,
            String optionSize,
            String optionColor,
            String mainImageUrl
    ) {
        this.sellerId = sellerId;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.productCategory = productCategory;
        this.productStatus = productStatus;
        this.optionSize = optionSize;
        this.optionColor = optionColor;
        this.mainImageUrl = mainImageUrl;
        syncProductStatusByAvailableQty();
    }

    private void syncProductStatusByAvailableQty() {
        if (this.availableQty == null || this.availableQty <= 0) {
            this.productStatus = InventoryProductStatus.SOLD_OUT.name();
            return;
        }
        if (this.productStatus == null || this.productStatus.isBlank()) {
            this.productStatus = InventoryProductStatus.RELEASED.name();
            return;
        }
        if (InventoryProductStatus.SOLD_OUT.name().equalsIgnoreCase(this.productStatus)) {
            this.productStatus = InventoryProductStatus.RELEASED.name();
        }
    }
}

package com.wearhouse.inventory.domain.entity;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "inventory_stock")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryStockEntity extends BaseEntity {

    public static final int STATUS_SOLD_OUT = 0;
    public static final int STATUS_ON_SALE = 1;

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

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "reserved_qty", nullable = false)
    private Integer reservedQty;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Builder
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
        this.status = resolveStatus(this.availableQty);
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
    }

    public static InventoryStockEntity create(
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
        this.status = resolveStatus(this.availableQty);
    }

    public void release(int quantity) {
        if (quantity <= 0) {
            return;
        }
        this.availableQty = this.availableQty + quantity;
        this.reservedQty = Math.max(0, this.reservedQty - quantity);
        this.status = resolveStatus(this.availableQty);
    }

    public void confirm(int quantity) {
        if (quantity <= 0) {
            return;
        }
        this.reservedQty = Math.max(0, this.reservedQty - quantity);
        this.status = resolveStatus(this.availableQty);
    }

    public void setAvailableQty(int availableQty) {
        this.availableQty = Math.max(availableQty, 0);
        this.status = resolveStatus(this.availableQty);
    }

    public void setStatus(int status) {
        if (status != STATUS_SOLD_OUT && status != STATUS_ON_SALE) {
            throw new IllegalArgumentException("재고 상태 값이 올바르지 않습니다.");
        }
        this.status = status;
    }

    public void setProductStatus(String productStatus) {
        this.productStatus = productStatus;
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
    }

    private int resolveStatus(int availableQty) {
        return availableQty <= 0 ? STATUS_SOLD_OUT : STATUS_ON_SALE;
    }
}

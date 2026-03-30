package com.wearhouse.cart.domain.entity;

import com.wearhouse.cart.infra.jpa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "cart_item",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_cart_item_buyer_product_option",
                        columnNames = {"buyer_id", "product_id", "option_id"}
                )
        },
        indexes = {
                @Index(name = "idx_cart_item_buyer_id", columnList = "buyer_id"),
                @Index(name = "idx_cart_item_buyer_updated_at", columnList = "buyer_id, updated_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItemEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "option_id", nullable = false)
    private Long optionId;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "main_image_url", nullable = false, length = 500)
    private String mainImageUrl;

    @Column(name = "size_label", nullable = false, length = 60)
    private String size;

    @Column(name = "color_label", nullable = false, length = 60)
    private String color;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    private CartItemEntity(
            Long buyerId,
            Long productId,
            Long optionId,
            String productName,
            String mainImageUrl,
            String size,
            String color,
            BigDecimal price,
            Integer quantity
    ) {
        this.buyerId = buyerId;
        this.productId = productId;
        this.optionId = optionId;
        this.productName = productName;
        this.mainImageUrl = mainImageUrl;
        this.size = size;
        this.color = color;
        this.price = price;
        this.quantity = quantity;
    }

    public static CartItemEntity of(
            Long buyerId,
            Long productId,
            Long optionId,
            String productName,
            String mainImageUrl,
            String size,
            String color,
            BigDecimal price,
            Integer quantity
    ) {
        return new CartItemEntity(
                buyerId,
                productId,
                optionId,
                productName,
                mainImageUrl,
                size,
                color,
                price,
                quantity
        );
    }

    public void updateSnapshot(
            String productName,
            String mainImageUrl,
            String size,
            String color,
            BigDecimal price
    ) {
        this.productName = productName;
        this.mainImageUrl = mainImageUrl;
        this.size = size;
        this.color = color;
        this.price = price;
    }

    public void updateQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}

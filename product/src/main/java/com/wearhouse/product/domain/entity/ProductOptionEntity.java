package com.wearhouse.product.domain.entity;

import com.wearhouse.product.infra.jpa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "product_option")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOptionEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(name = "size_label", nullable = false, length = 60)
    private String size;

    @Column(name = "color_label", nullable = false, length = 60)
    private String color;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Builder
    private ProductOptionEntity(
            ProductEntity product,
            String size,
            String color,
            Integer stockQuantity,
            Integer sortOrder
    ) {
        this.product = product;
        this.size=size;
        this.color=color;
        this.stockQuantity = stockQuantity == null ? 0 : stockQuantity;
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
    }

    public static ProductOptionEntity create(
            ProductEntity product,
            String size,
            String color,
            Integer stockQuantity,
            Integer sortOrder

    ) {
        return ProductOptionEntity.builder()
                .product(product)
                .size(size)
                .color(color)
                .stockQuantity(stockQuantity)
                .sortOrder(sortOrder)
                .build();
    }
}

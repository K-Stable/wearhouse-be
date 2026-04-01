package com.wearhouse.product.domain.entity;

import com.wearhouse.product.domain.model.ProductImageType;
import com.wearhouse.product.infra.jpa.common.BaseEntity;
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
@Table(name = "product_image")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImageEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_type", nullable = false, length = 20)
    private ProductImageType imageType;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    private ProductImageEntity(ProductEntity product, ProductImageType imageType, String imageUrl, Integer sortOrder) {
        this.product = product;
        this.imageType = imageType;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
    }

    public static ProductImageEntity of(ProductEntity product, ProductImageType imageType, String imageUrl, Integer sortOrder) {
        return new ProductImageEntity(
                product,
                imageType,
                imageUrl,
                sortOrder
        );
    }

    public Long getProductId() {
        return product == null ? null : product.getId();
    }
}

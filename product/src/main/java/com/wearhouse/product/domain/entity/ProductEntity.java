package com.wearhouse.product.domain.entity;

import com.wearhouse.product.domain.model.ProductImageType;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.infra.jpa.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "price", nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "category", nullable = false, length = 60)
    private String category;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status;

    @Column(name = "main_image_url", length = 500)
    private String mainImageUrl;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ProductOptionEntity> options = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("imageType ASC, sortOrder ASC, id ASC")
    private List<ProductImageEntity> images = new ArrayList<>();

    @Builder
    private ProductEntity(
            Long sellerId,
            String name,
            BigDecimal price,
            String category,
            String description,
            ProductStatus status,
            String mainImageUrl
    ) {
        this.sellerId = sellerId;
        this.name = name;
        this.price = price;
        this.category = category;
        this.description = description;
        this.status = status;
        this.mainImageUrl = mainImageUrl;
    }

    public static ProductEntity create(
            Long sellerId,
            String name,
            BigDecimal price,
            String category,
            String description,
            ProductStatus status,
            String mainImageUrl
    ) {
        return ProductEntity.builder()
                .sellerId(sellerId)
                .name(name)
                .price(price)
                .category(category)
                .description(description)
                .status(status)
                .mainImageUrl(mainImageUrl)
                .build();
    }

    public void addOption(String sizeLabel, String colorLabel, Integer stockQuantity, BigDecimal additionalPrice, int sortOrder) {
        ProductOptionEntity option = ProductOptionEntity.create(this, sizeLabel, colorLabel, stockQuantity, additionalPrice, sortOrder);
        this.options.add(option);
    }

    public void addImage(ProductImageType imageType, String imageUrl, int sortOrder) {
        ProductImageEntity image = ProductImageEntity.create(this, imageType, imageUrl, sortOrder);
        this.images.add(image);
    }

    public void updateStatus(ProductStatus status) {
        this.status = status;
    }
}

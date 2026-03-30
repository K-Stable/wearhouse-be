package com.wearhouse.product.domain.entity;

import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductImageType;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.infra.jpa.common.BaseEntity;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 60)
    private Category category;

    @Column(name = "details", length=500)
    private String details;

    @Column(name = "size_guide", length=500)
    private String sizeGuide;

    @Column(name = "shipping", length=500)
    private String shipping;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductOptionEntity> options = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("imageType ASC, sortOrder ASC, id ASC")
    private List<ProductImageEntity> images = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "season_id")
    private ProductSeasonEntity productSeason;

    private ProductEntity(
            Long sellerId,
            String name,
            BigDecimal price,
            Category category,
            String details,
            String sizeGuide,
            String shipping,
            ProductStatus status,
            ProductSeasonEntity productSeason
    ) {
        this.sellerId = sellerId;
        this.name = name;
        this.price = price;
        this.category = category;
        this.details = details;
        this.sizeGuide = sizeGuide;
        this.shipping = shipping;
        this.status = status;
        this.productSeason = productSeason;
    }

    public static ProductEntity of(
            Long sellerId,
            String name,
            BigDecimal price,
            Category category,
            String details,
            String sizeGuide,
            String shipping,
            ProductStatus status
    ) {
        return of(sellerId, name, price, category, details, sizeGuide, shipping, status, null);
    }

    public static ProductEntity of(
            Long sellerId,
            String name,
            BigDecimal price,
            Category category,
            String details,
            String sizeGuide,
            String shipping,
            ProductStatus status,
            ProductSeasonEntity productSeason
    ) {
        return new ProductEntity(
                sellerId,
                name,
                price,
                category,
                details,
                sizeGuide,
                shipping,
                status,
                productSeason
        );
    }

    public void addOption(String size, String color, Integer stockQuantity, Integer sortOrder) {
        ProductOptionEntity option = ProductOptionEntity.of(this, size, color, stockQuantity, sortOrder);
        this.options.add(option);
    }

    public void addImage(ProductImageType imageType, String imageUrl, Integer sortOrder) {
        ProductImageEntity image = ProductImageEntity.of(this, imageType, imageUrl, sortOrder);
        this.images.add(image);
    }

    public void updateStatus(ProductStatus status) {
        this.status = status;
    }

    public void assignSeason(ProductSeasonEntity season) {
        this.productSeason = season;
    }

    public void replaceOptions(List<OptionDraft> optionDrafts) {
        this.options.clear();
        if (optionDrafts == null || optionDrafts.isEmpty()) {
            return;
        }
        for (OptionDraft optionDraft : optionDrafts) {
            if (optionDraft == null) {
                continue;
            }
            addOption(
                    optionDraft.size(),
                    optionDraft.color(),
                    optionDraft.stockQuantity(),
                    optionDraft.sortOrder()
            );
        }
    }

    public void replaceImages(List<ImageDraft> imageDrafts) {
        this.images.clear();
        if (imageDrafts == null || imageDrafts.isEmpty()) {
            return;
        }
        for (ImageDraft imageDraft : imageDrafts) {
            if (imageDraft == null) {
                continue;
            }
            addImage(
                    imageDraft.imageType(),
                    imageDraft.imageUrl(),
                    imageDraft.sortOrder()
            );
        }
    }

    public record OptionDraft(
            String size,
            String color,
            Integer stockQuantity,
            Integer sortOrder
    ) {
    }

    public record ImageDraft(
            ProductImageType imageType,
            String imageUrl,
            Integer sortOrder
    ) {
    }
}

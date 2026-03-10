package com.wearhouse.product.domain.entity;

import com.wearhouse.product.infra.jpa.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(name = "product_season")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductSeasonEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @OneToMany(mappedBy = "productSeason", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductEntity> products = new ArrayList<>();

    @Builder
    private ProductSeasonEntity(Long sellerId, String name) {
        this.sellerId = sellerId;
        this.name = name;
    }

    public static ProductSeasonEntity create(Long sellerId, String name) {
        return ProductSeasonEntity.builder()
                .sellerId(sellerId)
                .name(name)
                .build();
    }

    public void addProduct(ProductEntity product) {
        if (product == null) {
            return;
        }
        if (!products.contains(product)) {
            products.add(product);
        }
        product.assignSeason(this);
    }
}

package com.wearhouse.product.domain.repository;

import com.wearhouse.product.domain.entity.ProductImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImageEntity, Long> {
}

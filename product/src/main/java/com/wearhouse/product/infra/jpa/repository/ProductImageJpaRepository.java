package com.wearhouse.product.infra.jpa.repository;

import com.wearhouse.product.domain.entity.ProductImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageJpaRepository extends JpaRepository<ProductImageEntity, Long> {
}

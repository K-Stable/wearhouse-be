package com.wearhouse.product.infra.jpa.repository;

import com.wearhouse.product.domain.entity.ProductOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionJpaRepository extends JpaRepository<ProductOptionEntity, Long> {
}

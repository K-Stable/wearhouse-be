package com.wearhouse.product.domain.repository;

import com.wearhouse.product.domain.entity.ProductImageEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImageEntity, Long> {

    List<ProductImageEntity> findAllByProduct_IdIn(Collection<Long> productIds);
}

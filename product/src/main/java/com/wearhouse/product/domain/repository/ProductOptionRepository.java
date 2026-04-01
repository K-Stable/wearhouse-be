package com.wearhouse.product.domain.repository;

import com.wearhouse.product.domain.entity.ProductOptionEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionRepository extends JpaRepository<ProductOptionEntity, Long> {

    List<ProductOptionEntity> findAllByProduct_IdIn(Collection<Long> productIds);
}

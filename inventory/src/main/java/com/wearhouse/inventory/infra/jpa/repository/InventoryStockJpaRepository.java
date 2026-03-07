package com.wearhouse.inventory.infra.jpa.repository;

import com.wearhouse.inventory.domain.entity.InventoryStockEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryStockJpaRepository extends JpaRepository<InventoryStockEntity, Long> {

    Optional<InventoryStockEntity> findBySkuId(Long skuId);

    List<InventoryStockEntity> findAllBySkuIdIn(Collection<Long> skuIds);
}

package com.wearhouse.inventory.infra.jpa.repository;

import com.wearhouse.inventory.domain.entity.InventoryStockEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryStockJpaRepository extends JpaRepository<InventoryStockEntity, Long> {

    Optional<InventoryStockEntity> findBySkuId(Long skuId);

    List<InventoryStockEntity> findAllBySkuIdIn(Collection<Long> skuIds);

    @Query("""
            SELECT stock
            FROM InventoryStockEntity stock
            WHERE stock.sellerId = :sellerId
              AND (:keyword IS NULL OR LOWER(stock.productName) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY stock.updatedAt DESC, stock.id DESC
            """)
    List<InventoryStockEntity> findSellerStocks(
            @Param("sellerId") Long sellerId,
            @Param("keyword") String keyword
    );
}

package com.wearhouse.inventory.domain.repository;

import com.wearhouse.inventory.domain.dto.response.SellerInventoryItemResponse;
import com.wearhouse.inventory.domain.entity.InventoryStockEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryStockRepository extends JpaRepository<InventoryStockEntity, Long> {

    Optional<InventoryStockEntity> findBySkuId(Long skuId);

    List<InventoryStockEntity> findAllBySkuIdIn(Collection<Long> skuIds);

    List<InventoryStockEntity> findAllByProductIdAndSellerId(Long productId, Long sellerId);

    @Query("""
            SELECT new com.wearhouse.inventory.domain.dto.response.SellerInventoryItemResponse(
                stock.productId,
                stock.mainImageUrl,
                stock.productName,
                stock.productPrice,
                stock.productCategory,
                stock.optionSize,
                stock.optionColor,
                stock.availableQty,
                stock.status
            )
            FROM InventoryStockEntity stock
            WHERE stock.sellerId = :sellerId
              AND (:keyword IS NULL OR LOWER(stock.productName) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY stock.updatedAt DESC, stock.id DESC
            """)
    List<SellerInventoryItemResponse> findSellerInventoryItems(
            @Param("sellerId") Long sellerId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    boolean existsByProductIdAndAvailableQtyGreaterThan(Long productId, Integer availableQty);
}

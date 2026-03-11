package com.wearhouse.product.domain.repository;

import com.wearhouse.product.domain.entity.ProductSeasonEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductSeasonRepository extends JpaRepository<ProductSeasonEntity, Long> {

    Optional<ProductSeasonEntity> findByIdAndSellerId(Long id, Long sellerId);

    @Query("""
            SELECT ps
            FROM ProductSeasonEntity ps
            WHERE ps.sellerId = :sellerId
              AND (:cursor IS NULL OR ps.id < :cursor)
            ORDER BY ps.id DESC
            """)
    List<ProductSeasonEntity> findSellerSeasons(
            @Param("sellerId") Long sellerId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    @Query("""
            SELECT DISTINCT ps
            FROM ProductSeasonEntity ps
            JOIN ps.products p
            WHERE p.status = com.wearhouse.product.domain.model.ProductStatus.RELEASED
              AND (:cursor IS NULL OR ps.id < :cursor)
            ORDER BY ps.id DESC
            """)
    List<ProductSeasonEntity> findBuyerSeasons(
            @Param("cursor") Long cursor,
            Pageable pageable
    );
}

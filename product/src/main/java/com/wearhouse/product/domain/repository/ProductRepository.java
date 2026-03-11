package com.wearhouse.product.domain.repository;

import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    @EntityGraph(attributePaths = {"options", "images"})
    @Query("""
            SELECT DISTINCT p
            FROM ProductEntity p
            WHERE p.sellerId = :sellerId
              AND (:status IS NULL OR p.status = :status)
              AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:cursor IS NULL OR p.id < :cursor)
              AND (:seasonId IS NULL OR p.productSeason.id = :seasonId)
            ORDER BY p.id DESC
            """)
    List<ProductEntity> findSellerProducts(
            @Param("sellerId") Long sellerId,
            @Param("status") ProductStatus status,
            @Param("keyword") String keyword,
            @Param("cursor") Long cursor,
            @Param("seasonId") Long seasonId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"options", "images"})
    Optional<ProductEntity> findByIdAndSellerId(Long id, Long sellerId);

    @EntityGraph(attributePaths = {"options", "images"})
    List<ProductEntity> findAllByIdInAndSellerId(Collection<Long> ids, Long sellerId);

    boolean existsByProductSeason_IdAndSellerId(Long seasonId, Long sellerId);

    @EntityGraph(attributePaths = {"options"})
    @Query("""
            SELECT DISTINCT p
            FROM ProductEntity p
            WHERE p.status = com.wearhouse.product.domain.model.ProductStatus.RELEASED
              AND (:category IS NULL OR p.category = :category)
              AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:cursor IS NULL OR p.id < :cursor)
            ORDER BY p.id DESC
            """)
    List<ProductEntity> findBuyerProducts(
            @Param("category") Category category,
            @Param("keyword") String keyword,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"options", "images"})
    Optional<ProductEntity> findByIdAndStatus(Long id, ProductStatus status);

    @EntityGraph(attributePaths = {"options"})
    List<ProductEntity> findTop8ByStatusAndCategoryAndIdNotOrderByIdDesc(ProductStatus status, Category category, Long id);
}

package com.wearhouse.product.infra.jpa.repository;

import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.model.ProductStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    @EntityGraph(attributePaths = {"options", "images"})
    @Query("""
            SELECT DISTINCT p
            FROM ProductEntity p
            WHERE p.sellerId = :sellerId
              AND (:status IS NULL OR p.status = :status)
              AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY p.id DESC
            """)
    List<ProductEntity> findSellerProducts(
            @Param("sellerId") Long sellerId,
            @Param("status") ProductStatus status,
            @Param("keyword") String keyword
    );

    @EntityGraph(attributePaths = {"options", "images"})
    Optional<ProductEntity> findByIdAndSellerId(Long id, Long sellerId);

    @EntityGraph(attributePaths = {"options"})
    @Query("""
            SELECT DISTINCT p
            FROM ProductEntity p
            WHERE p.status = com.wearhouse.product.domain.model.ProductStatus.RELEASED
              AND (:category IS NULL OR p.category = :category)
              AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY p.id DESC
            """)
    List<ProductEntity> findBuyerProducts(
            @Param("category") String category,
            @Param("keyword") String keyword
    );

    @EntityGraph(attributePaths = {"options", "images"})
    Optional<ProductEntity> findByIdAndStatus(Long id, ProductStatus status);

    @EntityGraph(attributePaths = {"options"})
    List<ProductEntity> findTop8ByStatusAndCategoryAndIdNotOrderByIdDesc(ProductStatus status, String category, Long id);
}

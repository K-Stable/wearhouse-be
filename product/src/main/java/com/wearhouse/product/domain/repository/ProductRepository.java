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

public interface ProductRepository extends JpaRepository<ProductEntity, Long>, ProductRepositoryCustom {

    @EntityGraph(attributePaths = {"options"})
    Optional<ProductEntity> findByIdAndSellerId(Long id, Long sellerId);

    @EntityGraph(attributePaths = {"options"})
    List<ProductEntity> findAllByIdInAndSellerId(Collection<Long> ids, Long sellerId);

    boolean existsByProductSeason_IdAndSellerId(Long seasonId, Long sellerId);

    @EntityGraph(attributePaths = {"options"})
    Optional<ProductEntity> findByIdAndStatus(Long id, ProductStatus status);

    @EntityGraph(attributePaths = {"options"})
    List<ProductEntity> findTop8ByStatusAndCategoryAndIdNotOrderByIdDesc(ProductStatus status, Category category, Long id);

    List<ProductEntity> findByStatusAndCategoryOrderByPriceDescIdDesc(
            ProductStatus status,
            Category category,
            Pageable pageable
    );

    List<ProductEntity> findBySellerIdAndNameContainingIgnoreCaseOrderByIdDesc(
            Long sellerId,
            String keyword,
            Pageable pageable
    );
}

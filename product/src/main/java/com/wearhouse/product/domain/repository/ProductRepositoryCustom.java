package com.wearhouse.product.domain.repository;

import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.model.BuyerProductSortType;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductStatus;
import java.util.List;

public interface ProductRepositoryCustom {

    List<ProductEntity> findBuyerProductsByCursor(
            Category category,
            Long cursorId,
            BuyerProductSortType sortType,
            int limit
    );

    List<ProductEntity> findSellerProductsByCursor(
            Long sellerId,
            ProductStatus status,
            String keyword,
            Long cursorId,
            Long seasonId,
            int limit
    );
}

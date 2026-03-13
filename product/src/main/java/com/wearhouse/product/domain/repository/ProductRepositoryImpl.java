package com.wearhouse.product.domain.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.QProductEntity;
import com.wearhouse.product.domain.model.BuyerProductSortType;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ProductEntity> findBuyerProductsByCursor(
            Category category,
            Long cursorId,
            BuyerProductSortType sortType,
            int limit
    ) {
        QProductEntity product = QProductEntity.productEntity;

        BooleanBuilder where = new BooleanBuilder();
        where.and(product.status.eq(ProductStatus.RELEASED));
        where.and(eqCategory(product, category));
        where.and(buildCursorPredicate(product, category, cursorId, sortType));

        return queryFactory.selectFrom(product)
                .where(where)
                .orderBy(orderSpecifiers(product, sortType))
                .limit(limit)
                .fetch();
    }

    @Override
    public List<ProductEntity> findSellerProductsByCursor(
            Long sellerId,
            ProductStatus status,
            String keyword,
            Long cursorId,
            Long seasonId,
            int limit
    ) {
        QProductEntity product = QProductEntity.productEntity;

        BooleanBuilder where = new BooleanBuilder();
        where.and(product.sellerId.eq(sellerId));
        where.and(eqProductStatus(product, status));
        where.and(containsProductName(product, keyword));
        where.and(ltProductId(product, cursorId));
        where.and(eqSeasonId(product, seasonId));

        return queryFactory.selectFrom(product)
                .where(where)
                .orderBy(product.id.desc())
                .limit(limit)
                .fetch();
    }

    private BooleanExpression buildCursorPredicate(
            QProductEntity product,
            Category category,
            Long cursorId,
            BuyerProductSortType sortType
    ) {
        if (cursorId == null) {
            return null;
        }

        Tuple cursorTuple = queryFactory
                .select(product.price, product.id)
                .from(product)
                .where(
                        product.id.eq(cursorId),
                        product.status.eq(ProductStatus.RELEASED),
                        eqCategory(product, category)
                )
                .fetchOne();

        if (cursorTuple == null) {
            return null;
        }

        BigDecimal cursorPrice = cursorTuple.get(product.price);
        Long resolvedCursorId = cursorTuple.get(product.id);
        if (cursorPrice == null || resolvedCursorId == null) {
            return null;
        }

        return switch (sortType) {
            case PRICE_HIGH ->
                    product.price.lt(cursorPrice)
                            .or(product.price.eq(cursorPrice).and(product.id.lt(resolvedCursorId)));
            case PRICE_LOW ->
                    product.price.gt(cursorPrice)
                            .or(product.price.eq(cursorPrice).and(product.id.lt(resolvedCursorId)));
            case LATEST -> product.id.lt(resolvedCursorId);
        };
    }

    private OrderSpecifier<?>[] orderSpecifiers(QProductEntity product, BuyerProductSortType sortType) {
        return switch (sortType) {
            case PRICE_HIGH -> new OrderSpecifier<?>[]{product.price.desc(), product.id.desc()};
            case PRICE_LOW -> new OrderSpecifier<?>[]{product.price.asc(), product.id.desc()};
            case LATEST -> new OrderSpecifier<?>[]{product.id.desc()};
        };
    }

    private BooleanExpression eqCategory(QProductEntity product, Category category) {
        return category == null ? null : product.category.eq(category);
    }

    private BooleanExpression eqProductStatus(QProductEntity product, ProductStatus status) {
        return status == null ? null : product.status.eq(status);
    }

    private BooleanExpression containsProductName(QProductEntity product, String keyword) {
        return keyword == null || keyword.isBlank() ? null : product.name.containsIgnoreCase(keyword);
    }

    private BooleanExpression ltProductId(QProductEntity product, Long cursorId) {
        return cursorId == null ? null : product.id.lt(cursorId);
    }

    private BooleanExpression eqSeasonId(QProductEntity product, Long seasonId) {
        return seasonId == null ? null : product.productSeason.id.eq(seasonId);
    }
}

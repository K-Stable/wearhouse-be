package com.wearhouse.product.domain.service.buyer;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.pagination.CursorPageResponse;
import com.wearhouse.common.global.pagination.CursorPaginationSupport;
import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.product.buyer.mapper.BuyerProductResponseMapper;
import com.wearhouse.product.domain.dto.response.BuyerProductDetailResponse;
import com.wearhouse.product.domain.dto.response.BuyerProductListResponse;
import com.wearhouse.product.domain.dto.response.ProductOptionResponse;
import com.wearhouse.product.domain.dto.response.ProductSeasonListResponse;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.ProductImageEntity;
import com.wearhouse.product.domain.entity.ProductSeasonEntity;
import com.wearhouse.product.domain.exception.ProductErrorCode;
import com.wearhouse.product.domain.model.BuyerProductSortType;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.repository.ProductImageRepository;
import com.wearhouse.product.domain.repository.ProductRepository;
import com.wearhouse.product.domain.repository.ProductSeasonRepository;
import com.wearhouse.product.domain.service.common.ProductStockResolver;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuyerProductQueryService {

    private static final int DEFAULT_LIMIT = 10;

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductSeasonRepository productSeasonRepository;
    private final ProductStockResolver productStockResolver;
    private final BuyerProductResponseMapper buyerProductResponseMapper;

    @Transactional(readOnly = true)
    public CursorPageResponse<BuyerProductListResponse> getBuyerProducts(
            Category category,
            BuyerProductSortType sort,
            Long cursorId,
            Integer limit
    ) {
        int pageLimit = resolveLimit(limit);
        BuyerProductSortType normalizedSort = sort == null ? BuyerProductSortType.LATEST : sort;
        List<ProductEntity> products = productRepository.findBuyerProductsByCursor(
                category,
                cursorId,
                normalizedSort,
                pageLimit + 1
        );
        Map<Long, List<ProductImageEntity>> imagesByProductId = loadImagesByProductId(products);
        return CursorPaginationSupport.toCursorPage(
                products,
                pageLimit,
                ProductEntity::getId,
                product -> buyerProductResponseMapper.toBuyerListResponse(
                        product,
                        imagesByProductId.getOrDefault(product.getId(), List.of())
                )
        );
    }

    @ReadTx
    public CursorPageResponse<ProductSeasonListResponse> getBuyerSeasons(Long cursor, Integer limit) {
        int pageLimit = resolveLimit(limit);
        List<ProductSeasonEntity> seasons = productSeasonRepository.findBuyerSeasons(
                cursor,
                PageRequest.of(0, pageLimit + 1)
        );
        return CursorPaginationSupport.toCursorPage(
                seasons,
                pageLimit,
                ProductSeasonEntity::getId,
                buyerProductResponseMapper::toSeasonListResponse
        );
    }

    @Transactional(readOnly = true)
    public BuyerProductDetailResponse getBuyerProductDetail(Long productId) {
        ProductEntity product = productRepository.findByIdAndStatus(productId, ProductStatus.RELEASED)
                .orElseThrow(() -> new ErrorException(ProductErrorCode.PRODUCT_NOT_FOUND));

        List<ProductEntity> similarProducts = productRepository
                .findTop8ByStatusAndCategoryAndIdNotOrderByIdDesc(ProductStatus.RELEASED, product.getCategory(), product.getId());
        List<BuyerProductListResponse> similarItems = similarProducts.isEmpty()
                ? null
                : similarProducts.stream()
                .limit(4)
                .map(buyerProductResponseMapper::toBuyerListResponse)
                .toList();

        Map<Long, Integer> stockQuantities = productStockResolver.resolveOptionStocks(product.getOptions());
        List<ProductOptionResponse> optionResponses =
                buyerProductResponseMapper.toOptionResponses(product.getOptions(), stockQuantities);

        return buyerProductResponseMapper.toBuyerProductDetailResponse(product, optionResponses, similarItems);
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return limit;
    }

    private Map<Long, List<ProductImageEntity>> loadImagesByProductId(List<ProductEntity> products) {
        List<Long> productIds = products.stream()
                .map(ProductEntity::getId)
                .filter(Objects::nonNull)
                .toList();
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return productImageRepository.findAllByProduct_IdIn(productIds).stream()
                .filter(image -> image.getProductId() != null)
                .collect(Collectors.groupingBy(ProductImageEntity::getProductId));
    }
}

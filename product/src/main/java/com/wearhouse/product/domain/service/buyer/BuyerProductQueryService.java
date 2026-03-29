package com.wearhouse.product.domain.service.buyer;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.pagination.CursorPageResponse;
import com.wearhouse.common.global.pagination.CursorPaginationSupport;
import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.product.domain.dto.response.BuyerProductDetailResponse;
import com.wearhouse.product.domain.dto.response.BuyerProductListResponse;
import com.wearhouse.product.domain.dto.response.ProductOptionResponse;
import com.wearhouse.product.domain.dto.response.ProductSeasonListResponse;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.ProductSeasonEntity;
import com.wearhouse.product.domain.exception.ProductErrorCode;
import com.wearhouse.product.domain.model.BuyerProductSortType;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.repository.ProductRepository;
import com.wearhouse.product.domain.repository.ProductSeasonRepository;
import com.wearhouse.product.domain.service.common.ProductStockResolver;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuyerProductQueryService {

    private static final int DEFAULT_LIMIT = 10;

    private final ProductRepository productRepository;
    private final ProductSeasonRepository productSeasonRepository;
    private final ProductStockResolver productStockResolver;
    private final BuyerProductResponseMapper buyerProductResponseMapper;

    @ReadTx
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
        return CursorPaginationSupport.toCursorPage(
                products,
                pageLimit,
                ProductEntity::getId,
                buyerProductResponseMapper::toBuyerListResponse
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

    @ReadTx
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
}

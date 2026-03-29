package com.wearhouse.product.domain.service.buyer;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.pagination.CursorPageResponse;
import com.wearhouse.common.global.pagination.CursorPaginationSupport;
import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.common.support.s3.S3StorageService;
import com.wearhouse.product.domain.dto.response.BuyerProductDetailResponse;
import com.wearhouse.product.domain.dto.response.BuyerProductListResponse;
import com.wearhouse.product.domain.dto.response.ProductOptionResponse;
import com.wearhouse.product.domain.dto.response.ProductSeasonListResponse;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.ProductImageEntity;
import com.wearhouse.product.domain.entity.ProductOptionEntity;
import com.wearhouse.product.domain.entity.ProductSeasonEntity;
import com.wearhouse.product.domain.exception.ProductErrorCode;
import com.wearhouse.product.domain.model.BuyerProductSortType;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductImageType;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.repository.ProductRepository;
import com.wearhouse.product.domain.repository.ProductSeasonRepository;
import com.wearhouse.product.infra.inventory.ProductInventoryClient;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuyerProductQueryService {

    private static final int DEFAULT_LIMIT = 10;

    private final ProductRepository productRepository;
    private final ProductSeasonRepository productSeasonRepository;
    private final ProductInventoryClient productInventoryClient;
    private final S3StorageService s3StorageService;

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
        return CursorPaginationSupport.toCursorPage(products, pageLimit, ProductEntity::getId, this::toBuyerListResponse);
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
                this::toSeasonListResponse
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
                : similarProducts.stream().limit(4).map(this::toBuyerListResponse).toList();

        return new BuyerProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                formatCategory(product.getCategory()),
                product.getDetails(),
                product.getSizeGuide(),
                product.getShipping(),
                extractMainImageUrl(product),
                extractImages(product, ProductImageType.PREVIEW),
                extractImages(product, ProductImageType.DETAIL),
                toOptionResponses(product.getOptions()),
                similarItems
        );
    }

    private BuyerProductListResponse toBuyerListResponse(ProductEntity product) {
        return new BuyerProductListResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                extractMainImageUrl(product)
        );
    }

    private ProductSeasonListResponse toSeasonListResponse(ProductSeasonEntity season) {
        return new ProductSeasonListResponse(
                season.getId(),
                season.getName()
        );
    }

    private String extractMainImageUrl(ProductEntity product) {
        return product.getImages().stream()
                .filter(image -> image.getImageType() == ProductImageType.MAIN)
                .sorted(Comparator.comparing(ProductImageEntity::getSortOrder).thenComparing(ProductImageEntity::getId))
                .map(ProductImageEntity::getImageUrl)
                .map(s3StorageService::getImageUrl)
                .findFirst()
                .orElse(null);
    }

    private List<String> extractImages(ProductEntity product, ProductImageType imageType) {
        return product.getImages().stream()
                .filter(image -> image.getImageType() == imageType)
                .sorted(Comparator.comparing(ProductImageEntity::getSortOrder).thenComparing(ProductImageEntity::getId))
                .map(ProductImageEntity::getImageUrl)
                .map(s3StorageService::getImageUrl)
                .toList();
    }

    private List<ProductOptionResponse> toOptionResponses(List<ProductOptionEntity> options) {
        Map<Long, Integer> stockQuantities = loadStockQuantities(options);
        return options.stream()
                .sorted(Comparator.comparing(ProductOptionEntity::getSortOrder).thenComparing(ProductOptionEntity::getId))
                .map(option -> new ProductOptionResponse(
                        option.getId(),
                        option.getSize(),
                        option.getColor(),
                        stockQuantities.getOrDefault(option.getId(), option.getStockQuantity())
                ))
                .toList();
    }

    private Map<Long, Integer> loadStockQuantities(List<ProductOptionEntity> options) {
        Map<Long, Integer> stockByOptionId = new HashMap<>();
        for (ProductOptionEntity option : options) {
            stockByOptionId.put(option.getId(), option.getStockQuantity());
        }

        List<Long> optionIds = options.stream()
                .map(ProductOptionEntity::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (optionIds.isEmpty()) {
            return stockByOptionId;
        }

        try {
            Map<Long, Integer> availableByOptionId = productInventoryClient.getAvailableQtyBulk(optionIds);
            for (Long optionId : optionIds) {
                Integer availableQty = availableByOptionId.get(optionId);
                if (availableQty != null) {
                    stockByOptionId.put(optionId, availableQty);
                }
            }
        } catch (RuntimeException ignored) {
            // inventory 조회 실패 시 product DB stock 값으로 응답한다.
        }
        return stockByOptionId;
    }

    private String formatCategory(Category category) {
        String upper = category.name();
        return upper.substring(0, 1) + upper.substring(1).toLowerCase(Locale.ROOT);
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return limit;
    }
}

package com.wearhouse.product.domain.service.seller;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.pagination.CursorPageResponse;
import com.wearhouse.common.global.pagination.CursorPaginationSupport;
import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.product.domain.dto.response.BuyerProductDetailResponse;
import com.wearhouse.product.domain.dto.response.BuyerProductListResponse;
import com.wearhouse.product.domain.dto.response.ProductOptionResponse;
import com.wearhouse.product.domain.dto.response.ProductSeasonListResponse;
import com.wearhouse.product.domain.dto.response.SellerProductListResponse;
import com.wearhouse.product.domain.dto.response.SellerProductResponse;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.ProductImageEntity;
import com.wearhouse.product.domain.entity.ProductOptionEntity;
import com.wearhouse.product.domain.entity.ProductSeasonEntity;
import com.wearhouse.product.domain.exception.ProductErrorCode;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductImageType;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.repository.ProductRepository;
import com.wearhouse.product.domain.repository.ProductSeasonRepository;
import com.wearhouse.product.infra.inventory.ProductInventoryClient;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SellerProductQueryService {

    private static final String SELLER_USER_TYPE = "SELLER";

    private final ProductRepository productRepository;
    private final ProductSeasonRepository productSeasonRepository;
    private final ProductInventoryClient productInventoryClient;

    @ReadTx
    @Transactional(readOnly = true)
    public CursorPageResponse<SellerProductListResponse> getSellerProducts(
            LoginUser currentUser,
            ProductStatus status,
            String keyword,
            Long cursor,
            Integer limit,
            Long seasonId
    ) {
        Long sellerId = requireSeller(currentUser);
        validateSeasonOwnership(sellerId, seasonId);
        int normalizedLimit = CursorPaginationSupport.normalizeLimit(limit);
        List<ProductEntity> products = productRepository.findSellerProducts(
                sellerId,
                status,
                normalizeKeyword(keyword),
                cursor,
                seasonId,
                PageRequest.of(0, normalizedLimit + 1)
        );
        return CursorPaginationSupport.toCursorPage(products, normalizedLimit, ProductEntity::getId, this::toSellerListResponse);
    }

    @ReadTx
    @Transactional(readOnly = true)
    public CursorPageResponse<BuyerProductListResponse> getBuyerProducts(
            String category,
            String keyword,
            Long cursor,
            Integer limit
    ) {
        int normalizedLimit = CursorPaginationSupport.normalizeLimit(limit);
        Category normalizedCategory = normalizeCategory(category);
        List<ProductEntity> products = productRepository.findBuyerProducts(
                normalizedCategory,
                normalizeKeyword(keyword),
                cursor,
                PageRequest.of(0, normalizedLimit + 1)
        );
        return CursorPaginationSupport.toCursorPage(products, normalizedLimit, ProductEntity::getId, this::toBuyerListResponse);
    }

    @ReadTx
    @Transactional(readOnly = true)
    public CursorPageResponse<ProductSeasonListResponse> getSellerSeasons(
            LoginUser currentUser,
            Long cursor,
            Integer limit
    ) {
        Long sellerId = requireSeller(currentUser);
        int normalizedLimit = CursorPaginationSupport.normalizeLimit(limit);
        List<ProductSeasonEntity> seasons = productSeasonRepository.findSellerSeasons(
                sellerId,
                cursor,
                PageRequest.of(0, normalizedLimit + 1)
        );
        return CursorPaginationSupport.toCursorPage(
                seasons,
                normalizedLimit,
                ProductSeasonEntity::getId,
                this::toSeasonListResponse
        );
    }

    @ReadTx
    @Transactional(readOnly = true)
    public ProductSeasonListResponse getSellerSeason(LoginUser currentUser, Long seasonId) {
        Long sellerId = requireSeller(currentUser);
        ProductSeasonEntity season = productSeasonRepository.findByIdAndSellerId(seasonId, sellerId)
                .orElseThrow(() -> new ErrorException(ProductErrorCode.PRODUCT_SEASON_NOT_FOUND));
        return toSeasonListResponse(season);
    }

    @ReadTx
    @Transactional(readOnly = true)
    public CursorPageResponse<ProductSeasonListResponse> getBuyerSeasons(Long cursor, Integer limit) {
        int normalizedLimit = CursorPaginationSupport.normalizeLimit(limit);
        List<ProductSeasonEntity> seasons = productSeasonRepository.findBuyerSeasons(
                cursor,
                PageRequest.of(0, normalizedLimit + 1)
        );
        return CursorPaginationSupport.toCursorPage(
                seasons,
                normalizedLimit,
                ProductSeasonEntity::getId,
                this::toSeasonListResponse
        );
    }

    @ReadTx
    @Transactional(readOnly = true)
    public SellerProductResponse getSellerProduct(LoginUser currentUser, Long productId) {
        Long sellerId = requireSeller(currentUser);
        ProductEntity product = productRepository.findByIdAndSellerId(productId, sellerId)
                .orElseThrow(() -> new ErrorException(ProductErrorCode.PRODUCT_NOT_FOUND));
        return toSellerProductResponse(product);
    }

    @ReadTx
    @Transactional(readOnly = true)
    public BuyerProductDetailResponse getBuyerProductDetail(Long productId) {
        ProductEntity product = productRepository.findByIdAndStatus(productId, ProductStatus.RELEASED)
                .orElseThrow(() -> new ErrorException(ProductErrorCode.PRODUCT_NOT_FOUND));

        List<ProductEntity> similarProducts = productRepository
                .findTop8ByStatusAndCategoryAndIdNotOrderByIdDesc(ProductStatus.RELEASED, product.getCategory(), product.getId());

        return new BuyerProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                formatCategory(product.getCategory()),
                product.getDetails(),
                extractMainImageUrl(product),
                extractImages(product, ProductImageType.PREVIEW),
                extractImages(product, ProductImageType.DETAIL),
                toOptionResponses(product.getOptions()),
                similarProducts.stream().limit(4).map(this::toBuyerListResponse).toList()
        );
    }

    private SellerProductListResponse toSellerListResponse(ProductEntity product) {
        Map<Long, Integer> stockQuantities = loadStockQuantities(product.getOptions());
        Set<String> sizes = new LinkedHashSet<>();
        Set<String> colors = new LinkedHashSet<>();
        int totalStock = 0;
        for (ProductOptionEntity option : product.getOptions()) {
            sizes.add(option.getSize());
            colors.add(option.getColor());
            totalStock += stockQuantities.getOrDefault(option.getId(), 0);
        }

        return new SellerProductListResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                formatCategory(product.getCategory()),
                product.getStatus(),
                extractMainImageUrl(product),
                List.copyOf(sizes),
                List.copyOf(colors),
                totalStock
        );
    }

    private SellerProductResponse toSellerProductResponse(ProductEntity product) {
        return new SellerProductResponse(
                product.getId(),
                product.getSellerId(),
                product.getName(),
                product.getPrice(),
                formatCategory(product.getCategory()),
                product.getDetails(),
                product.getStatus(),
                extractMainImageUrl(product),
                extractImages(product, ProductImageType.PREVIEW),
                extractImages(product, ProductImageType.DETAIL),
                toOptionResponses(product.getOptions())
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
                .findFirst()
                .orElse(null);
    }

    private List<String> extractImages(ProductEntity product, ProductImageType imageType) {
        return product.getImages().stream()
                .filter(image -> image.getImageType() == imageType)
                .sorted(Comparator.comparing(ProductImageEntity::getSortOrder).thenComparing(ProductImageEntity::getId))
                .map(ProductImageEntity::getImageUrl)
                .toList();
    }

    private List<ProductOptionResponse> toOptionResponses(List<ProductOptionEntity> options) {
        return options.stream()
                .sorted(Comparator.comparing(ProductOptionEntity::getSortOrder).thenComparing(ProductOptionEntity::getId))
                .map(option -> new ProductOptionResponse(
                        option.getId(),
                        option.getSize(),
                        option.getColor()
                ))
                .toList();
    }

    private Map<Long, Integer> loadStockQuantities(List<ProductOptionEntity> options) {
        Map<Long, Integer> stockByOptionId = new HashMap<>();
        for (ProductOptionEntity option : options) {
            if (option.getId() == null) {
                stockByOptionId.put(null, option.getStockQuantity());
                continue;
            }
            Integer availableQty;
            try {
                availableQty = productInventoryClient.getAvailableQty(option.getId());
            } catch (RuntimeException exception) {
                throw new ErrorException(ProductErrorCode.INVENTORY_STOCK_SYNC_FAILED);
            }
            stockByOptionId.put(option.getId(), availableQty);
        }
        return stockByOptionId;
    }

    private Long requireSeller(LoginUser currentUser) {
        if (currentUser == null || currentUser.userId() == null || !SELLER_USER_TYPE.equalsIgnoreCase(currentUser.userType())) {
            throw new ErrorException(ProductErrorCode.FORBIDDEN_PRODUCT_ACCESS);
        }
        return currentUser.userId();
    }

    private void validateSeasonOwnership(Long sellerId, Long seasonId) {
        if (seasonId == null) {
            return;
        }
        productSeasonRepository.findByIdAndSellerId(seasonId, sellerId)
                .orElseThrow(() -> new ErrorException(ProductErrorCode.PRODUCT_SEASON_NOT_FOUND));
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private Category normalizeCategory(String rawCategory) {
        String normalized = rawCategory == null || rawCategory.isBlank() ? null : rawCategory.trim();
        if (normalized == null) {
            return null;
        }
        try {
            return Category.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ErrorException(ProductErrorCode.INVALID_PRODUCT_CATEGORY);
        }
    }

    private String formatCategory(Category category) {
        String upper = category.name();
        return upper.substring(0, 1) + upper.substring(1).toLowerCase(Locale.ROOT);
    }
}

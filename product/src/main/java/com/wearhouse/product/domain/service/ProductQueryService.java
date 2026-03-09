package com.wearhouse.product.domain.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.product.domain.dto.response.BuyerProductDetailResponse;
import com.wearhouse.product.domain.dto.response.BuyerProductListResponse;
import com.wearhouse.product.domain.dto.response.ProductOptionResponse;
import com.wearhouse.product.domain.dto.response.SellerProductListResponse;
import com.wearhouse.product.domain.dto.response.SellerProductResponse;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.ProductImageEntity;
import com.wearhouse.product.domain.entity.ProductOptionEntity;
import com.wearhouse.product.domain.exception.ProductErrorCode;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductImageType;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.infra.inventory.ProductInventoryClient;
import com.wearhouse.product.domain.repository.ProductRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.wearhouse.common.global.transactional.ReadTx;

@Service
public class ProductQueryService {

    private final ProductRepository productRepository;
    private final ProductInventoryClient productInventoryClient;

    public ProductQueryService(
            ProductRepository productRepository,
            ProductInventoryClient productInventoryClient
    ) {
        this.productRepository = productRepository;
        this.productInventoryClient = productInventoryClient;
    }

    @ReadTx
    public List<SellerProductListResponse> getSellerProducts(
            LoginUser currentUser,
            ProductStatus status,
            String keyword,
            int limit
    ) {
        Long sellerId = requireSeller(currentUser);
        String normalizedKeyword = normalizeKeyword(keyword);
        int normalizedLimit = normalizeLimit(limit, 100);

        List<ProductEntity> products = productRepository.findSellerProducts(sellerId, status, normalizedKeyword);
        return products.stream()
                .limit(normalizedLimit)
                .map(this::toSellerListResponse)
                .toList();
    }

    @ReadTx
    public SellerProductResponse getSellerProduct(LoginUser currentUser, Long productId) {
        Long sellerId = requireSeller(currentUser);
        ProductEntity product = productRepository.findByIdAndSellerId(productId, sellerId)
                .orElseThrow(() -> new ErrorException(ProductErrorCode.PRODUCT_NOT_FOUND));
        return toSellerProductResponse(product);
    }

    @ReadTx
    public List<BuyerProductListResponse> getBuyerProducts(String category, String keyword, String sort, int limit) {
        Category normalizedCategory = normalizeCategory(category);
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedSort = normalizeSort(sort);
        int normalizedLimit = normalizeLimit(limit, 100);

        List<ProductEntity> products = new ArrayList<>(productRepository.findBuyerProducts(normalizedCategory, normalizedKeyword));
        applySort(products, normalizedSort);

        return products.stream()
                .limit(normalizedLimit)
                .map(product -> new BuyerProductListResponse(
                        product.getId(),
                        product.getName(),
                        product.getPrice(),
                        formatCategory(product.getCategory()),
                        product.getMainImageUrl(),
                        false
                ))
                .toList();
    }

    @ReadTx
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
                product.getDescription(),
                product.getMainImageUrl(),
                extractImages(product, ProductImageType.PREVIEW),
                extractImages(product, ProductImageType.DETAIL),
                toOptionResponsesFromProduct(product.getOptions()),
                similarProducts.stream().limit(4).map(similar -> new BuyerProductListResponse(
                        similar.getId(),
                        similar.getName(),
                        similar.getPrice(),
                        formatCategory(similar.getCategory()),
                        similar.getMainImageUrl(),
                        false
                )).toList()
        );
    }

    private SellerProductListResponse toSellerListResponse(ProductEntity product) {
        Map<Long, Integer> stockQuantities = loadStockQuantities(product.getOptions());
        Set<String> sizes = new LinkedHashSet<>();
        Set<String> colors = new LinkedHashSet<>();
        int totalStock = 0;
        for (ProductOptionEntity option : product.getOptions()) {
            sizes.add(option.getSizeLabel());
            colors.add(option.getColorLabel());
            totalStock += stockQuantities.getOrDefault(option.getId(), 0);
        }

        return new SellerProductListResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                formatCategory(product.getCategory()),
                product.getStatus(),
                product.getMainImageUrl(),
                List.copyOf(sizes),
                List.copyOf(colors),
                totalStock
        );
    }

    private SellerProductResponse toSellerProductResponse(ProductEntity product) {
        Map<Long, Integer> stockQuantities = loadStockQuantities(product.getOptions());
        return new SellerProductResponse(
                product.getId(),
                product.getSellerId(),
                product.getName(),
                product.getPrice(),
                formatCategory(product.getCategory()),
                product.getDescription(),
                product.getStatus(),
                product.getMainImageUrl(),
                extractImages(product, ProductImageType.PREVIEW),
                extractImages(product, ProductImageType.DETAIL),
                toOptionResponses(product.getOptions(), stockQuantities)
        );
    }

    private List<String> extractImages(ProductEntity product, ProductImageType imageType) {
        return product.getImages().stream()
                .filter(image -> image.getImageType() == imageType)
                .sorted(Comparator.comparing(ProductImageEntity::getSortOrder).thenComparing(ProductImageEntity::getId))
                .map(ProductImageEntity::getImageUrl)
                .toList();
    }

    private List<ProductOptionResponse> toOptionResponses(
            List<ProductOptionEntity> options,
            Map<Long, Integer> stockQuantities
    ) {
        return options.stream()
                .sorted(Comparator.comparing(ProductOptionEntity::getSortOrder).thenComparing(ProductOptionEntity::getId))
                .map(option -> new ProductOptionResponse(
                        option.getId(),
                        option.getSizeLabel(),
                        option.getColorLabel(),
                        stockQuantities.getOrDefault(option.getId(), 0),
                        option.getAdditionalPrice()
                ))
                .toList();
    }

    private List<ProductOptionResponse> toOptionResponsesFromProduct(List<ProductOptionEntity> options) {
        return options.stream()
                .sorted(Comparator.comparing(ProductOptionEntity::getSortOrder).thenComparing(ProductOptionEntity::getId))
                .map(option -> new ProductOptionResponse(
                        option.getId(),
                        option.getSizeLabel(),
                        option.getColorLabel(),
                        option.getStockQuantity(),
                        option.getAdditionalPrice()
                ))
                .toList();
    }

    private void applySort(List<ProductEntity> products, String sort) {
        switch (sort) {
            case "priceAsc" -> products.sort(Comparator.comparing(ProductEntity::getPrice).thenComparing(ProductEntity::getId));
            case "priceDesc" -> products.sort(Comparator.comparing(ProductEntity::getPrice).reversed().thenComparing(ProductEntity::getId, Comparator.reverseOrder()));
            default -> products.sort(Comparator.comparing(ProductEntity::getId).reversed());
        }
    }

    private Long requireSeller(LoginUser currentUser) {
        if (currentUser == null || currentUser.userId() == null || !"SELLER".equalsIgnoreCase(currentUser.userType())) {
            throw new ErrorException(ProductErrorCode.FORBIDDEN_PRODUCT_ACCESS);
        }
        return currentUser.userId();
    }

    private String normalizeKeyword(String keyword) {
        return normalizeBlank(keyword);
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Category normalizeCategory(String rawCategory) {
        String normalized = normalizeBlank(rawCategory);
        if (normalized == null) {
            return null;
        }
        try {
            return Category.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ErrorException(ProductErrorCode.INVALID_PRODUCT_CATEGORY);
        }
    }

    private Map<Long, Integer> loadStockQuantities(List<ProductOptionEntity> options) {
        Map<Long, Integer> stockByOptionId = new HashMap<>();
        for (ProductOptionEntity option : options) {
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

    private int normalizeLimit(int value, int max) {
        if (value <= 0) {
            return 20;
        }
        return Math.min(value, max);
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "latest";
        }
        return sort.trim();
    }

    private String formatCategory(Category category) {
        String upper = category.name();
        return upper.substring(0, 1) + upper.substring(1).toLowerCase(Locale.ROOT);
    }
}

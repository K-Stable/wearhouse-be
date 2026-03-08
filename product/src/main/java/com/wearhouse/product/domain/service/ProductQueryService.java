package com.wearhouse.product.domain.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.security.current.CurrentUserPrincipal;
import com.wearhouse.product.domain.dto.response.BuyerProductDetailResponse;
import com.wearhouse.product.domain.dto.response.BuyerProductListResponse;
import com.wearhouse.product.domain.dto.response.ProductOptionResponse;
import com.wearhouse.product.domain.dto.response.SellerProductListResponse;
import com.wearhouse.product.domain.dto.response.SellerProductResponse;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.ProductImageEntity;
import com.wearhouse.product.domain.entity.ProductOptionEntity;
import com.wearhouse.product.domain.exception.ProductErrorCode;
import com.wearhouse.product.domain.model.ProductImageType;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.infra.jpa.repository.ProductJpaRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductQueryService {

    private final ProductJpaRepository productJpaRepository;

    public ProductQueryService(ProductJpaRepository productJpaRepository) {
        this.productJpaRepository = productJpaRepository;
    }

    @Transactional(readOnly = true)
    public List<SellerProductListResponse> getSellerProducts(
            CurrentUserPrincipal currentUser,
            ProductStatus status,
            String keyword,
            int limit
    ) {
        Long sellerId = requireSeller(currentUser);
        String normalizedKeyword = normalizeKeyword(keyword);
        int normalizedLimit = normalizeLimit(limit, 100);

        List<ProductEntity> products = productJpaRepository.findSellerProducts(sellerId, status, normalizedKeyword);
        return products.stream()
                .limit(normalizedLimit)
                .map(this::toSellerListResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SellerProductResponse getSellerProduct(CurrentUserPrincipal currentUser, Long productId) {
        Long sellerId = requireSeller(currentUser);
        ProductEntity product = productJpaRepository.findByIdAndSellerId(productId, sellerId)
                .orElseThrow(() -> new ErrorException(ProductErrorCode.PRODUCT_NOT_FOUND));
        return toSellerProductResponse(product);
    }

    @Transactional(readOnly = true)
    public List<BuyerProductListResponse> getBuyerProducts(String category, String keyword, String sort, int limit) {
        String normalizedCategory = normalizeBlank(category);
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedSort = normalizeSort(sort);
        int normalizedLimit = normalizeLimit(limit, 100);

        List<ProductEntity> products = new ArrayList<>(productJpaRepository.findBuyerProducts(normalizedCategory, normalizedKeyword));
        applySort(products, normalizedSort);

        return products.stream()
                .limit(normalizedLimit)
                .map(product -> new BuyerProductListResponse(
                        product.getId(),
                        product.getName(),
                        product.getPrice(),
                        product.getCategory(),
                        product.getMainImageUrl(),
                        false
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public BuyerProductDetailResponse getBuyerProductDetail(Long productId) {
        ProductEntity product = productJpaRepository.findByIdAndStatus(productId, ProductStatus.RELEASED)
                .orElseThrow(() -> new ErrorException(ProductErrorCode.PRODUCT_NOT_FOUND));

        List<ProductEntity> similarProducts = productJpaRepository
                .findTop8ByStatusAndCategoryAndIdNotOrderByIdDesc(ProductStatus.RELEASED, product.getCategory(), product.getId());

        return new BuyerProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCategory(),
                product.getDescription(),
                product.getMainImageUrl(),
                extractImages(product, ProductImageType.PREVIEW),
                extractImages(product, ProductImageType.DETAIL),
                toOptionResponses(product.getOptions()),
                similarProducts.stream().limit(4).map(similar -> new BuyerProductListResponse(
                        similar.getId(),
                        similar.getName(),
                        similar.getPrice(),
                        similar.getCategory(),
                        similar.getMainImageUrl(),
                        false
                )).toList()
        );
    }

    private SellerProductListResponse toSellerListResponse(ProductEntity product) {
        Set<String> sizes = new LinkedHashSet<>();
        Set<String> colors = new LinkedHashSet<>();
        int totalStock = 0;
        for (ProductOptionEntity option : product.getOptions()) {
            sizes.add(option.getSizeLabel());
            colors.add(option.getColorLabel());
            totalStock += option.getStockQuantity() == null ? 0 : option.getStockQuantity();
        }

        return new SellerProductListResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCategory(),
                product.getStatus(),
                product.getMainImageUrl(),
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
                product.getCategory(),
                product.getDescription(),
                product.getStatus(),
                product.getMainImageUrl(),
                extractImages(product, ProductImageType.PREVIEW),
                extractImages(product, ProductImageType.DETAIL),
                toOptionResponses(product.getOptions())
        );
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

    private Long requireSeller(CurrentUserPrincipal currentUser) {
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
}

package com.wearhouse.product.domain.service.seller;

import com.wearhouse.common.global.pagination.CursorPageResponse;
import com.wearhouse.common.global.pagination.CursorPaginationSupport;
import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.product.seller.mapper.SellerProductResponseMapper;
import com.wearhouse.product.domain.dto.response.ProductSeasonListResponse;
import com.wearhouse.product.domain.dto.response.SellerProductListResponse;
import com.wearhouse.product.domain.dto.response.SellerProductResponse;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.ProductImageEntity;
import com.wearhouse.product.domain.entity.ProductOptionEntity;
import com.wearhouse.product.domain.entity.ProductSeasonEntity;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.repository.ProductImageRepository;
import com.wearhouse.product.domain.repository.ProductOptionRepository;
import com.wearhouse.product.domain.repository.ProductRepository;
import com.wearhouse.product.domain.repository.ProductSeasonRepository;
import com.wearhouse.product.domain.service.common.ProductStockResolver;
import java.util.ArrayList;
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
public class SellerProductQueryService {

    private static final int DEFAULT_LIMIT = 20;

    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductSeasonRepository productSeasonRepository;
    private final SellerProductAccessValidator sellerProductAccessValidator;
    private final ProductStockResolver productStockResolver;
    private final SellerProductResponseMapper sellerProductResponseMapper;

    @Transactional(readOnly = true)
    public CursorPageResponse<SellerProductListResponse> getSellerProducts(
            LoginUser currentUser,
            ProductStatus status,
            String keyword,
            Long cursor,
            Integer limit,
            Long seasonId
    ) {
        Long sellerId = sellerProductAccessValidator.requireSellerId(currentUser);
        sellerProductAccessValidator.validateSeasonOwnership(sellerId, seasonId);
        int pageLimit = resolveLimit(limit);
        List<ProductEntity> products = productRepository.findSellerProductsByCursor(
                sellerId,
                status,
                normalizeKeyword(keyword),
                cursor,
                seasonId,
                pageLimit + 1
        );
        Map<Long, List<ProductOptionEntity>> optionsByProductId = loadOptionsByProductId(products);
        Map<Long, List<ProductImageEntity>> imagesByProductId = loadImagesByProductId(products);
        Map<Long, Integer> stockByOptionId = productStockResolver.resolveOptionStocks(extractAllOptions(optionsByProductId));
        return CursorPaginationSupport.toCursorPage(
                products,
                pageLimit,
                ProductEntity::getId,
                product -> sellerProductResponseMapper.toSellerListResponse(
                        product,
                        optionsByProductId.getOrDefault(product.getId(), List.of()),
                        imagesByProductId.getOrDefault(product.getId(), List.of()),
                        stockByOptionId
                )
        );
    }

    @ReadTx
    public CursorPageResponse<ProductSeasonListResponse> getSellerSeasons(
            LoginUser currentUser,
            Long cursor,
            Integer limit
    ) {
        Long sellerId = sellerProductAccessValidator.requireSellerId(currentUser);
        int pageLimit = resolveLimit(limit);
        List<ProductSeasonEntity> seasons = productSeasonRepository.findSellerSeasons(
                sellerId,
                cursor,
                PageRequest.of(0, pageLimit + 1)
        );
        return CursorPaginationSupport.toCursorPage(
                seasons,
                pageLimit,
                ProductSeasonEntity::getId,
                sellerProductResponseMapper::toSeasonListResponse
        );
    }

    @ReadTx
    public ProductSeasonListResponse getSellerSeason(LoginUser currentUser, Long seasonId) {
        Long sellerId = sellerProductAccessValidator.requireSellerId(currentUser);
        ProductSeasonEntity season = sellerProductAccessValidator.requireSeason(sellerId, seasonId);
        return sellerProductResponseMapper.toSeasonListResponse(season);
    }

    @Transactional(readOnly = true)
    public SellerProductResponse getSellerProduct(LoginUser currentUser, Long productId) {
        Long sellerId = sellerProductAccessValidator.requireSellerId(currentUser);
        ProductEntity product = sellerProductAccessValidator.requireProduct(sellerId, productId);
        Map<Long, Integer> stockByOptionId = productStockResolver.resolveOptionStocks(product.getOptions());
        return sellerProductResponseMapper.toSellerProductResponse(product, stockByOptionId);
    }

    private List<ProductOptionEntity> extractAllOptions(Map<Long, List<ProductOptionEntity>> optionsByProductId) {
        List<ProductOptionEntity> options = new ArrayList<>();
        for (List<ProductOptionEntity> optionEntities : optionsByProductId.values()) {
            options.addAll(optionEntities);
        }
        return options;
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return limit;
    }

    private Map<Long, List<ProductOptionEntity>> loadOptionsByProductId(List<ProductEntity> products) {
        List<Long> productIds = products.stream()
                .map(ProductEntity::getId)
                .filter(Objects::nonNull)
                .toList();
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return productOptionRepository.findAllByProduct_IdIn(productIds).stream()
                .filter(option -> option.getProductId() != null)
                .collect(Collectors.groupingBy(ProductOptionEntity::getProductId));
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

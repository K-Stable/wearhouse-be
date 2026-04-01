package com.wearhouse.product.buyer.mapper;

import com.wearhouse.product.domain.dto.response.BuyerProductDetailResponse;
import com.wearhouse.product.domain.dto.response.BuyerProductListResponse;
import com.wearhouse.product.domain.dto.response.ProductOptionResponse;
import com.wearhouse.product.domain.dto.response.ProductSeasonListResponse;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.ProductImageEntity;
import com.wearhouse.product.domain.entity.ProductOptionEntity;
import com.wearhouse.product.domain.entity.ProductSeasonEntity;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductImageType;
import com.wearhouse.product.domain.service.common.ProductImageUrlResolver;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BuyerProductResponseMapper {

    private final ProductImageUrlResolver productImageUrlResolver;

    public BuyerProductListResponse toBuyerListResponse(ProductEntity product) {
        return new BuyerProductListResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                productImageUrlResolver.resolveMainImageUrl(product)
        );
    }

    public BuyerProductListResponse toBuyerListResponse(ProductEntity product, List<ProductImageEntity> images) {
        return new BuyerProductListResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                productImageUrlResolver.resolveMainImageUrl(images)
        );
    }

    public ProductSeasonListResponse toSeasonListResponse(ProductSeasonEntity season) {
        return new ProductSeasonListResponse(
                season.getId(),
                season.getName()
        );
    }

    public List<ProductOptionResponse> toOptionResponses(
            List<ProductOptionEntity> options,
            Map<Long, Integer> stockQuantities
    ) {
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

    public BuyerProductDetailResponse toBuyerProductDetailResponse(
            ProductEntity product,
            List<ProductOptionResponse> optionResponses,
            List<BuyerProductListResponse> similarItems
    ) {
        return new BuyerProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                formatCategory(product.getCategory()),
                product.getDetails(),
                product.getSizeGuide(),
                product.getShipping(),
                productImageUrlResolver.resolveMainImageUrl(product),
                productImageUrlResolver.resolveImageUrls(product, ProductImageType.PREVIEW),
                productImageUrlResolver.resolveImageUrls(product, ProductImageType.DETAIL),
                optionResponses,
                similarItems
        );
    }

    private String formatCategory(Category category) {
        String upper = category.name();
        return upper.substring(0, 1) + upper.substring(1).toLowerCase(Locale.ROOT);
    }
}

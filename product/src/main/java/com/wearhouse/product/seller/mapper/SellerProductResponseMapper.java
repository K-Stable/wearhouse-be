package com.wearhouse.product.seller.mapper;

import com.wearhouse.product.domain.dto.response.ProductOptionResponse;
import com.wearhouse.product.domain.dto.response.ProductSeasonListResponse;
import com.wearhouse.product.domain.dto.response.SellerProductListResponse;
import com.wearhouse.product.domain.dto.response.SellerProductResponse;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.ProductOptionEntity;
import com.wearhouse.product.domain.entity.ProductSeasonEntity;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductImageType;
import com.wearhouse.product.domain.service.common.ProductImageUrlResolver;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SellerProductResponseMapper {

    private final ProductImageUrlResolver productImageUrlResolver;

    public ProductSeasonListResponse toSeasonListResponse(ProductSeasonEntity season) {
        return new ProductSeasonListResponse(
                season.getId(),
                season.getName()
        );
    }

    public SellerProductListResponse toSellerListResponse(ProductEntity product, Map<Long, Integer> stockQuantities) {
        Set<String> sizes = new LinkedHashSet<>();
        Set<String> colors = new LinkedHashSet<>();
        int totalStock = 0;
        for (ProductOptionEntity option : product.getOptions()) {
            sizes.add(option.getSize());
            colors.add(option.getColor());
            totalStock += stockQuantities.getOrDefault(option.getId(), option.getStockQuantity());
        }

        return new SellerProductListResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                formatCategory(product.getCategory()),
                product.getStatus(),
                productImageUrlResolver.resolveMainImageUrl(product),
                List.copyOf(sizes),
                List.copyOf(colors),
                totalStock
        );
    }

    public SellerProductResponse toSellerProductResponse(
            ProductEntity product,
            Map<Long, Integer> stockQuantities
    ) {
        return new SellerProductResponse(
                product.getId(),
                product.getSellerId(),
                product.getName(),
                product.getPrice(),
                formatCategory(product.getCategory()),
                product.getDetails(),
                product.getStatus(),
                productImageUrlResolver.resolveMainImageUrl(product),
                productImageUrlResolver.resolveImageUrls(product, ProductImageType.PREVIEW),
                productImageUrlResolver.resolveImageUrls(product, ProductImageType.DETAIL),
                toOptionResponses(product.getOptions(), stockQuantities)
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

    private String formatCategory(Category category) {
        String upper = category.name();
        return upper.substring(0, 1) + upper.substring(1).toLowerCase(Locale.ROOT);
    }
}

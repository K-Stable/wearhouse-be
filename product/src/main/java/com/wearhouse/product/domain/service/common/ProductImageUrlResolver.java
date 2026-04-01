package com.wearhouse.product.domain.service.common;

import com.wearhouse.common.support.s3.S3StorageService;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.ProductImageEntity;
import com.wearhouse.product.domain.model.ProductImageType;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductImageUrlResolver {

    private final S3StorageService s3StorageService;

    public String resolveImageUrl(String imageKey) {
        return s3StorageService.getImageUrl(imageKey);
    }

    public String resolveMainImageUrl(ProductEntity product) {
        return sortedImages(product, ProductImageType.MAIN).stream()
                .map(ProductImageEntity::getImageUrl)
                .map(s3StorageService::getImageUrl)
                .findFirst()
                .orElse(null);
    }

    public String resolveMainImageUrl(List<ProductImageEntity> images) {
        return sortedImages(images, ProductImageType.MAIN).stream()
                .map(ProductImageEntity::getImageUrl)
                .map(s3StorageService::getImageUrl)
                .findFirst()
                .orElse(null);
    }

    public List<String> resolveImageUrls(ProductEntity product, ProductImageType imageType) {
        return sortedImages(product, imageType).stream()
                .map(ProductImageEntity::getImageUrl)
                .map(s3StorageService::getImageUrl)
                .toList();
    }

    public List<String> resolveImageUrls(List<ProductImageEntity> images, ProductImageType imageType) {
        return sortedImages(images, imageType).stream()
                .map(ProductImageEntity::getImageUrl)
                .map(s3StorageService::getImageUrl)
                .toList();
    }

    private List<ProductImageEntity> sortedImages(ProductEntity product, ProductImageType imageType) {
        return product.getImages().stream()
                .filter(image -> image.getImageType() == imageType)
                .sorted(Comparator.comparing(ProductImageEntity::getSortOrder).thenComparing(ProductImageEntity::getId))
                .toList();
    }

    private List<ProductImageEntity> sortedImages(List<ProductImageEntity> images, ProductImageType imageType) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        return images.stream()
                .filter(image -> image.getImageType() == imageType)
                .sorted(Comparator.comparing(ProductImageEntity::getSortOrder).thenComparing(ProductImageEntity::getId))
                .toList();
    }
}

package com.wearhouse.product.domain.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.security.current.CurrentUserPrincipal;
import com.wearhouse.product.domain.dto.request.ProductCreateRequest;
import com.wearhouse.product.domain.dto.request.ProductOptionCreateRequest;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.exception.ProductErrorCode;
import com.wearhouse.product.domain.model.ProductImageType;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.infra.jpa.repository.ProductJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductCommandService {

    private final ProductJpaRepository productJpaRepository;

    public ProductCommandService(ProductJpaRepository productJpaRepository) {
        this.productJpaRepository = productJpaRepository;
    }

    @Transactional
    public Long createProduct(CurrentUserPrincipal currentUser, ProductCreateRequest request) {
        Long sellerId = requireSeller(currentUser);
        validateOptions(request);

        ProductStatus status = request.status() == null ? ProductStatus.PENDING : request.status();
        ProductEntity product = ProductEntity.create(
                sellerId,
                request.name(),
                request.price(),
                request.category(),
                request.description(),
                status,
                request.mainImageUrl()
        );

        int optionSort = 0;
        for (ProductOptionCreateRequest optionRequest : request.options()) {
            product.addOption(
                    optionRequest.size(),
                    optionRequest.color(),
                    optionRequest.stockQuantity(),
                    optionRequest.additionalPrice(),
                    optionSort++
            );
        }

        int previewSort = 0;
        if (request.previewImageUrls() != null) {
            for (String previewImageUrl : request.previewImageUrls()) {
                if (previewImageUrl == null || previewImageUrl.isBlank()) {
                    continue;
                }
                product.addImage(ProductImageType.PREVIEW, previewImageUrl, previewSort++);
            }
        }

        int detailSort = 0;
        if (request.detailImageUrls() != null) {
            for (String detailImageUrl : request.detailImageUrls()) {
                if (detailImageUrl == null || detailImageUrl.isBlank()) {
                    continue;
                }
                product.addImage(ProductImageType.DETAIL, detailImageUrl, detailSort++);
            }
        }

        ProductEntity saved = productJpaRepository.save(product);
        return saved.getId();
    }

    @Transactional
    public void updateProductStatus(CurrentUserPrincipal currentUser, Long productId, ProductStatus status) {
        Long sellerId = requireSeller(currentUser);
        ProductEntity product = productJpaRepository.findByIdAndSellerId(productId, sellerId)
                .orElseThrow(() -> new ErrorException(ProductErrorCode.PRODUCT_NOT_FOUND));
        product.updateStatus(status);
    }

    @Transactional
    public void deleteProduct(CurrentUserPrincipal currentUser, Long productId) {
        Long sellerId = requireSeller(currentUser);
        ProductEntity product = productJpaRepository.findByIdAndSellerId(productId, sellerId)
                .orElseThrow(() -> new ErrorException(ProductErrorCode.PRODUCT_NOT_FOUND));
        productJpaRepository.delete(product);
    }

    private Long requireSeller(CurrentUserPrincipal currentUser) {
        if (currentUser == null || currentUser.userId() == null || !"SELLER".equalsIgnoreCase(currentUser.userType())) {
            throw new ErrorException(ProductErrorCode.FORBIDDEN_PRODUCT_ACCESS);
        }
        return currentUser.userId();
    }

    private void validateOptions(ProductCreateRequest request) {
        if (request.options() == null || request.options().isEmpty()) {
            throw new ErrorException(ProductErrorCode.PRODUCT_OPTION_REQUIRED);
        }
        for (ProductOptionCreateRequest option : request.options()) {
            if (option.size() == null || option.size().isBlank() || option.color() == null || option.color().isBlank()) {
                throw new ErrorException(ProductErrorCode.PRODUCT_OPTION_INVALID);
            }
            if (option.stockQuantity() == null || option.stockQuantity() < 0) {
                throw new ErrorException(ProductErrorCode.PRODUCT_OPTION_INVALID);
            }
        }
    }
}

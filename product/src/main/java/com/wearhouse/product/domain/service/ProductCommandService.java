package com.wearhouse.product.domain.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.product.domain.dto.request.ProductCreateRequest;
import com.wearhouse.product.domain.dto.request.ProductOptionCreateRequest;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.ProductOptionEntity;
import com.wearhouse.product.domain.exception.ProductErrorCode;
import com.wearhouse.product.domain.model.ProductImageType;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.infra.inventory.ProductInventoryClient;
import com.wearhouse.product.domain.repository.ProductRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.wearhouse.common.global.transactional.WriteTx;

@Service
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final ProductInventoryClient productInventoryClient;

    public ProductCommandService(
            ProductRepository productRepository,
            ProductInventoryClient productInventoryClient
    ) {
        this.productRepository = productRepository;
        this.productInventoryClient = productInventoryClient;
    }

    @WriteTx
    public void createProduct(LoginUser currentUser, ProductCreateRequest request) {
        Long sellerId = requireSeller(currentUser);
        validateOptions(request);

        ProductStatus status = request.status() == null ? ProductStatus.PENDING : request.status();
        ProductEntity product = ProductEntity.create(
                sellerId,
                request.name(),
                request.price(),
                request.category(),
                request.description(),
                request.mainImageUrl(),
                status
        );

        List<ProductOptionCreateRequest> optionRequests = request.options();
        int optionSort = 0;
        for (ProductOptionCreateRequest optionRequest : optionRequests) {
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
                product.addImage(ProductImageType.PREVIEW, previewImageUrl.trim(), previewSort++);
            }
        }

        int detailSort = 0;
        if (request.detailImageUrls() != null) {
            for (String detailImageUrl : request.detailImageUrls()) {
                if (detailImageUrl == null || detailImageUrl.isBlank()) {
                    continue;
                }
                product.addImage(ProductImageType.DETAIL, detailImageUrl.trim(), detailSort++);
            }
        }

        ProductEntity saved = productRepository.save(product);
        syncOptionStocks(saved, optionRequests);
    }

    @WriteTx
    public void updateProductStatus(LoginUser currentUser, Long productId, ProductStatus status) {
        Long sellerId = requireSeller(currentUser);
        ProductEntity product = productRepository.findByIdAndSellerId(productId, sellerId)
                .orElseThrow(() -> new ErrorException(ProductErrorCode.PRODUCT_NOT_FOUND));
        product.updateStatus(status);
    }

    @WriteTx
    public void deleteProduct(LoginUser currentUser, Long productId) {
        Long sellerId = requireSeller(currentUser);
        ProductEntity product = productRepository.findByIdAndSellerId(productId, sellerId)
                .orElseThrow(() -> new ErrorException(ProductErrorCode.PRODUCT_NOT_FOUND));
        productRepository.delete(product);
    }

    private Long requireSeller(LoginUser currentUser) {
        if (currentUser == null || currentUser.userId() == null || !"SELLER".equalsIgnoreCase(currentUser.userType())) {
            throw new ErrorException(ProductErrorCode.FORBIDDEN_PRODUCT_ACCESS);
        }
        return currentUser.userId();
    }

    private void validateOptions(ProductCreateRequest request) {
        if (request.options() == null || request.options().isEmpty()) {
            throw new ErrorException(ProductErrorCode.PRODUCT_OPTION_REQUIRED);
        }
        Set<String> uniqueOptionKeys = new HashSet<>();
        for (ProductOptionCreateRequest option : request.options()) {
            if (option.size() == null || option.size().isBlank() || option.color() == null || option.color().isBlank()) {
                throw new ErrorException(ProductErrorCode.PRODUCT_OPTION_INVALID);
            }
            if (option.stockQuantity() == null || option.stockQuantity() < 0) {
                throw new ErrorException(ProductErrorCode.PRODUCT_OPTION_INVALID);
            }
            if (option.additionalPrice() != null && option.additionalPrice().signum() < 0) {
                throw new ErrorException(ProductErrorCode.PRODUCT_OPTION_INVALID);
            }

            String optionKey = option.size().trim().toLowerCase(Locale.ROOT)
                    + "::"
                    + option.color().trim().toLowerCase(Locale.ROOT);
            if (!uniqueOptionKeys.add(optionKey)) {
                throw new ErrorException(ProductErrorCode.PRODUCT_OPTION_INVALID);
            }
        }
    }

    private void syncOptionStocks(ProductEntity product, List<ProductOptionCreateRequest> optionRequests) {
        List<ProductOptionEntity> sortedOptions = product.getOptions().stream()
                .sorted(Comparator.comparing(ProductOptionEntity::getSortOrder).thenComparing(ProductOptionEntity::getId))
                .toList();

        for (int i = 0; i < sortedOptions.size(); i++) {
            ProductOptionEntity optionEntity = sortedOptions.get(i);
            int stockQuantity = optionRequests.get(i).stockQuantity();
            try {
                productInventoryClient.upsertStock(optionEntity.getId(), stockQuantity);
            } catch (RuntimeException exception) {
                throw new ErrorException(ProductErrorCode.INVENTORY_STOCK_SYNC_FAILED);
            }
        }
    }
}

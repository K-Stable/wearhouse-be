package com.wearhouse.product.domain.service.seller;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.product.domain.dto.request.ProductCreateRequest;
import com.wearhouse.product.domain.dto.request.ProductOptionCreateRequest;
import com.wearhouse.product.domain.dto.request.ProductSeasonCreateRequest;
import com.wearhouse.product.domain.dto.request.ProductSeasonUpdateRequest;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.ProductOptionEntity;
import com.wearhouse.product.domain.entity.ProductSeasonEntity;
import com.wearhouse.product.domain.exception.ProductErrorCode;
import com.wearhouse.product.domain.model.ProductImageType;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.repository.ProductRepository;
import com.wearhouse.product.domain.repository.ProductSeasonRepository;
import com.wearhouse.product.infra.inventory.ProductInventoryClient;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SellerProductCommandService {

    private static final Logger log = LoggerFactory.getLogger(SellerProductCommandService.class);
    private static final String SELLER_USER_TYPE = "SELLER";

    private final ProductRepository productRepository;
    private final ProductSeasonRepository productSeasonRepository;
    private final ProductInventoryClient productInventoryClient;

    @WriteTx
    public void createProductSeason(LoginUser currentUser, ProductSeasonCreateRequest request) {
        Long sellerId = getSellerId(currentUser);
        ProductSeasonEntity productSeason = ProductSeasonEntity.create(sellerId, normalizeSeasonName(request.name()));
        productSeasonRepository.save(productSeason);
    }

    @WriteTx
    public void updateProductSeason(LoginUser currentUser, Long seasonId, ProductSeasonUpdateRequest request) {
        Long sellerId = getSellerId(currentUser);
        ProductSeasonEntity season = productSeasonRepository.findByIdAndSellerId(seasonId, sellerId)
                .orElseThrow(() -> new ErrorException(ProductErrorCode.PRODUCT_SEASON_NOT_FOUND));
        season.updateName(normalizeSeasonName(request.name()));
    }

    @WriteTx
    public void deleteProductSeason(LoginUser currentUser, Long seasonId) {
        Long sellerId = getSellerId(currentUser);
        ProductSeasonEntity season = productSeasonRepository.findByIdAndSellerId(seasonId, sellerId)
                .orElseThrow(() -> new ErrorException(ProductErrorCode.PRODUCT_SEASON_NOT_FOUND));
        if (productRepository.existsByProductSeason_IdAndSellerId(seasonId, sellerId)) {
            throw new ErrorException(ProductErrorCode.PRODUCT_SEASON_IN_USE);
        }
        productSeasonRepository.delete(season);
    }

    @WriteTx
    public void createProduct(LoginUser currentUser, Long seasonId, ProductCreateRequest request) {
        Long sellerId = getSellerId(currentUser);
        String mainImageUrl = normalizeImageUrl(request.mainImageUrl());
        ProductSeasonEntity season = resolveSeason(sellerId, seasonId);
        ProductEntity product = buildProduct(request, sellerId, mainImageUrl, season);

        ProductEntity saved = productRepository.saveAndFlush(product);
        upsertInventoryStocks(saved, mainImageUrl);
    }

    @WriteTx
    public void updateProductStatus(LoginUser currentUser, Long productId, ProductStatus status) {
        Long sellerId = getSellerId(currentUser);
        ProductEntity product = productRepository.findByIdAndSellerId(productId, sellerId)
                .orElseThrow(() -> new ErrorException(ProductErrorCode.PRODUCT_NOT_FOUND));
        product.updateStatus(status);
    }

    @WriteTx
    public void updateProductStatuses(LoginUser currentUser, List<Long> productIds, ProductStatus status) {
        Long sellerId = getSellerId(currentUser);
        Set<Long> targetIds = productIds == null
                ? Set.of()
                : productIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        if (targetIds.isEmpty()) {
            throw new ErrorException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }

        List<ProductEntity> products = productRepository.findAllByIdInAndSellerId(targetIds, sellerId);
        if (products.size() != targetIds.size()) {
            throw new ErrorException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }

        for (ProductEntity product : products) {
            product.updateStatus(status);
        }
    }

    @WriteTx
    public void deleteProduct(LoginUser currentUser, Long productId) {
        Long sellerId = getSellerId(currentUser);
        ProductEntity product = productRepository.findByIdAndSellerId(productId, sellerId)
                .orElseThrow(() -> new ErrorException(ProductErrorCode.PRODUCT_NOT_FOUND));
        productInventoryClient.deleteProductStocks(productId);
        productRepository.delete(product);
    }

    @WriteTx
    public void markProductsSoldOut(List<Long> productIds) {
        Set<Long> targetIds = productIds == null
                ? Set.of()
                : productIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        if (targetIds.isEmpty()) {
            return;
        }

        List<ProductEntity> products = productRepository.findAllById(targetIds);
        for (ProductEntity product : products) {
            product.updateStatus(ProductStatus.SOLD_OUT);
        }
    }

    private Long getSellerId(LoginUser currentUser) {
        if (currentUser == null || currentUser.userId() == null || !SELLER_USER_TYPE.equalsIgnoreCase(currentUser.userType())) {
            throw new ErrorException(ProductErrorCode.FORBIDDEN_PRODUCT_ACCESS);
        }
        return currentUser.userId();
    }

    private ProductEntity buildProduct(
            ProductCreateRequest request,
            Long sellerId,
            String mainImageUrl,
            ProductSeasonEntity season
    ) {
        ProductEntity product = ProductEntity.create(
                sellerId,
                request.name(),
                request.price(),
                request.category(),
                request.details(),
                request.sizeGuide(),
                request.shipping(),
                resolveStatus(request.status()),
                season
        );
        addOptions(product, request.options());
        product.addImage(ProductImageType.MAIN, mainImageUrl, 0);
        addOptionalImages(product, ProductImageType.PREVIEW, request.previewImageUrls());
        addOptionalImages(product, ProductImageType.DETAIL, request.detailImageUrls());
        return product;
    }

    private ProductStatus resolveStatus(ProductStatus requestedStatus) {
        return requestedStatus == null ? ProductStatus.PENDING : requestedStatus;
    }

    private int resolveInventoryStatus(ProductStatus productStatus, int stockQuantity) {
        if (productStatus == ProductStatus.SOLD_OUT || stockQuantity <= 0) {
            return 0;
        }
        return 1;
    }

    private ProductSeasonEntity resolveSeason(Long sellerId, Long seasonId) {
        return productSeasonRepository.findByIdAndSellerId(seasonId, sellerId)
                .orElseThrow(() -> new ErrorException(ProductErrorCode.PRODUCT_SEASON_NOT_FOUND));
    }

    private String normalizeSeasonName(String name) {
        return name == null ? null : name.trim();
    }

    private String normalizeImageUrl(String imageUrl) {
        return imageUrl.trim();
    }

    private void addOptions(ProductEntity product, List<ProductOptionCreateRequest> optionRequests) {
        int optionSort = 0;
        for (ProductOptionCreateRequest optionRequest : optionRequests) {
            product.addOption(
                    optionRequest.size(),
                    optionRequest.color(),
                    optionRequest.stockQuantity(),
                    optionSort++
            );
        }
    }

    private void addOptionalImages(ProductEntity product, ProductImageType imageType, List<String> imageUrls) {
        if (imageUrls == null) {
            return;
        }
        int sortOrder = 0;
        for (String imageUrl : imageUrls) {
            if (imageUrl == null || imageUrl.isBlank()) {
                continue;
            }
            product.addImage(imageType, normalizeImageUrl(imageUrl), sortOrder++);
        }
    }

    private void upsertInventoryStocks(ProductEntity product, String mainImageUrl) {
        if (product == null) {
            throw new ErrorException(ProductErrorCode.INVENTORY_STOCK_SYNC_FAILED);
        }
        List<ProductOptionEntity> sortedOptions = product.getOptions().stream()
                .sorted(Comparator.comparing(ProductOptionEntity::getSortOrder).thenComparing(ProductOptionEntity::getId))
                .toList();

        for (int i = 0; i < sortedOptions.size(); i++) {
            ProductOptionEntity optionEntity = sortedOptions.get(i);
            Long optionId = optionEntity.getId();
            int stockQuantity = optionEntity.getStockQuantity();
            if (optionId == null) {
                log.error("상품 옵션 ID가 없어 inventory 동기화를 진행할 수 없습니다. productId={}, optionIndex={}",
                        product.getId(), i);
                throw new ErrorException(ProductErrorCode.INVENTORY_STOCK_SYNC_FAILED);
            }
            try {
                productInventoryClient.upsertStock(
                        optionId,
                        stockQuantity,
                        resolveInventoryStatus(product.getStatus(), stockQuantity),
                        product.getSellerId(),
                        product.getId(),
                        product.getName(),
                        product.getPrice(),
                        product.getCategory().name(),
                        optionEntity.getSize(),
                        optionEntity.getColor(),
                        mainImageUrl
                );
            } catch (RuntimeException exception) {
                log.error("inventory 동기화 실패 productId={}, optionId={}, stockQuantity={}, message={}",
                        product.getId(), optionId, stockQuantity, exception.getMessage(), exception);
                throw new ErrorException(ProductErrorCode.INVENTORY_STOCK_SYNC_FAILED);
            }
        }
    }
}

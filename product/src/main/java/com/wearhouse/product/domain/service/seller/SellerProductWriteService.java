package com.wearhouse.product.domain.service.seller;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.product.domain.dto.request.ProductCreateRequest;
import com.wearhouse.product.domain.dto.request.ProductOptionCreateRequest;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.ProductSeasonEntity;
import com.wearhouse.product.domain.exception.ProductErrorCode;
import com.wearhouse.product.domain.model.ProductImageType;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.repository.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SellerProductWriteService {

    private final ProductRepository productRepository;
    private final SellerProductAccessValidator sellerProductAccessValidator;
    private final SellerProductInventorySyncService sellerProductInventorySyncService;

    @WriteTx
    public void createProduct(LoginUser currentUser, Long seasonId, ProductCreateRequest request) {
        Long sellerId = sellerProductAccessValidator.requireSellerId(currentUser);
        ProductSeasonEntity season = sellerProductAccessValidator.requireSeason(sellerId, seasonId);
        ProductEntity product = buildProduct(request, sellerId, season);

        ProductEntity saved = productRepository.saveAndFlush(product);
        sellerProductInventorySyncService.syncProduct(saved);
    }

    @WriteTx
    public void updateProductStatus(LoginUser currentUser, Long productId, ProductStatus status) {
        Long sellerId = sellerProductAccessValidator.requireSellerId(currentUser);
        ProductEntity product = sellerProductAccessValidator.requireProduct(sellerId, productId);
        product.updateStatus(status);
        sellerProductInventorySyncService.syncProduct(product);
    }

    @WriteTx
    public void updateProductStatuses(LoginUser currentUser, List<Long> productIds, ProductStatus status) {
        Long sellerId = sellerProductAccessValidator.requireSellerId(currentUser);
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
            sellerProductInventorySyncService.syncProduct(product);
        }
    }

    @WriteTx
    public void deleteProduct(LoginUser currentUser, Long productId) {
        Long sellerId = sellerProductAccessValidator.requireSellerId(currentUser);
        ProductEntity product = sellerProductAccessValidator.requireProduct(sellerId, productId);
        sellerProductInventorySyncService.deleteProductStocks(productId);
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
            sellerProductInventorySyncService.syncProduct(product);
        }
    }

    private ProductEntity buildProduct(
            ProductCreateRequest request,
            Long sellerId,
            ProductSeasonEntity season
    ) {
        ProductEntity product = ProductEntity.of(
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
        product.replaceOptions(buildOptionDrafts(request.options()));
        product.replaceImages(buildImageDrafts(request.mainImageUrl(), request.previewImageUrls(), request.detailImageUrls()));
        return product;
    }

    private ProductStatus resolveStatus(ProductStatus requestedStatus) {
        return requestedStatus == null ? ProductStatus.PENDING : requestedStatus;
    }

    private List<ProductEntity.OptionDraft> buildOptionDrafts(List<ProductOptionCreateRequest> optionRequests) {
        List<ProductEntity.OptionDraft> optionDrafts = new ArrayList<>();
        int optionSort = 0;
        for (ProductOptionCreateRequest optionRequest : optionRequests) {
            optionDrafts.add(new ProductEntity.OptionDraft(
                    optionRequest.size(),
                    optionRequest.color(),
                    optionRequest.stockQuantity(),
                    optionSort++
            ));
        }
        return optionDrafts;
    }

    private List<ProductEntity.ImageDraft> buildImageDrafts(
            String mainImageKey,
            List<String> previewImageKeys,
            List<String> detailImageKeys
    ) {
        List<ProductEntity.ImageDraft> imageDrafts = new ArrayList<>();
        imageDrafts.add(new ProductEntity.ImageDraft(ProductImageType.MAIN, normalizeImageKey(mainImageKey), 0));
        appendOptionalImages(imageDrafts, ProductImageType.PREVIEW, previewImageKeys);
        appendOptionalImages(imageDrafts, ProductImageType.DETAIL, detailImageKeys);
        return imageDrafts;
    }

    private void appendOptionalImages(
            List<ProductEntity.ImageDraft> imageDrafts,
            ProductImageType imageType,
            List<String> imageKeys
    ) {
        if (imageKeys == null) {
            return;
        }
        int sortOrder = 0;
        for (String imageKey : imageKeys) {
            if (imageKey == null || imageKey.isBlank()) {
                continue;
            }
            imageDrafts.add(new ProductEntity.ImageDraft(imageType, normalizeImageKey(imageKey), sortOrder++));
        }
    }

    private String normalizeImageKey(String imageKey) {
        return imageKey.trim();
    }
}

package com.wearhouse.product.domain.service.seller;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.ProductOptionEntity;
import com.wearhouse.product.domain.exception.ProductErrorCode;
import com.wearhouse.product.domain.service.common.ProductImageUrlResolver;
import com.wearhouse.product.infra.inventory.ProductInventoryClient;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerProductInventorySyncService {

    private final ProductInventoryClient productInventoryClient;
    private final ProductImageUrlResolver productImageUrlResolver;

    public void syncProduct(ProductEntity product) {
        if (product == null) {
            throw new ErrorException(ProductErrorCode.INVENTORY_STOCK_SYNC_FAILED);
        }

        String mainImageUrl = productImageUrlResolver.resolveMainImageUrl(product);
        if (mainImageUrl == null || mainImageUrl.isBlank()) {
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
                log.error(
                        "상품 옵션 ID가 없어 inventory 동기화를 진행할 수 없습니다. productId={}, optionIndex={}",
                        product.getId(),
                        i
                );
                throw new ErrorException(ProductErrorCode.INVENTORY_STOCK_SYNC_FAILED);
            }
            try {
                productInventoryClient.upsertStock(
                        optionId,
                        stockQuantity,
                        product.getStatus().name(),
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
                log.error(
                        "inventory 동기화 실패 productId={}, optionId={}, stockQuantity={}, message={}",
                        product.getId(),
                        optionId,
                        stockQuantity,
                        exception.getMessage(),
                        exception
                );
                throw new ErrorException(ProductErrorCode.INVENTORY_STOCK_SYNC_FAILED);
            }
        }
    }

    public void deleteProductStocks(Long productId) {
        productInventoryClient.deleteProductStocks(productId);
    }
}

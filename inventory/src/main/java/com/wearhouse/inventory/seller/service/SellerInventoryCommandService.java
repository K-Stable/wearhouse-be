package com.wearhouse.inventory.seller.service;

import com.wearhouse.common.global.error.CommonErrorCode;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.inventory.seller.dto.request.InventoryStockUpdateRequest;
import com.wearhouse.inventory.seller.dto.request.InventoryStockUpsertRequest;
import com.wearhouse.inventory.seller.dto.response.InventoryStockResponse;
import com.wearhouse.inventory.domain.entity.InventoryStockEntity;
import com.wearhouse.inventory.domain.exception.InventoryErrorCode;
import com.wearhouse.inventory.domain.model.InventoryProductStatus;
import com.wearhouse.inventory.domain.repository.InventoryStockRepository;
import com.wearhouse.inventory.infra.redis.InventoryRedisStockCacheService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SellerInventoryCommandService {

    private final InventoryStockRepository inventoryStockRepository;
    private final InventoryRedisStockCacheService inventoryRedisStockCacheService;

    @WriteTx
    public InventoryStockResponse upsertStock(InventoryStockUpsertRequest request) {
        validateUpsertRequest(request);
        String normalizedProductStatus = normalizeProductStatus(request.productStatus());

        InventoryStockEntity stock = inventoryStockRepository.findBySkuId(request.skuId())
                .orElseGet(() -> InventoryStockEntity.create(
                        request.skuId(),
                        request.availableQty(),
                        request.sellerId(),
                        request.productId(),
                        request.productName(),
                        request.productPrice(),
                        request.category(),
                        normalizedProductStatus,
                        request.size(),
                        request.color(),
                        request.mainImageUrl()
                ));

        stock.setAvailableQty(request.availableQty());
        stock.updateSnapshot(
                request.sellerId(),
                request.productId(),
                request.productName(),
                request.productPrice(),
                request.category(),
                normalizedProductStatus,
                request.size(),
                request.color(),
                request.mainImageUrl()
        );
        InventoryStockEntity saved = inventoryStockRepository.save(stock);
        inventoryRedisStockCacheService.cacheAvailableQty(saved.getSkuId(), saved.getAvailableQty());
        return InventoryStockResponse.from(saved);
    }

    @WriteTx
    public InventoryStockResponse updateSellerInventory(LoginUser currentUser, Long skuId, InventoryStockUpdateRequest request) {
        Long sellerId = requireSeller(currentUser);
        if (request == null || (request.availableQty() == null && request.productStatus() == null)) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
        if (request.availableQty() != null && request.availableQty() < 0) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
        String normalizedProductStatus = null;
        if (request.productStatus() != null) {
            normalizedProductStatus = normalizeProductStatus(request.productStatus());
            if (normalizedProductStatus == null) {
                throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
            }
        }
        if (request.availableQty() == null && normalizedProductStatus == null) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }

        InventoryStockEntity stock = inventoryStockRepository.findBySkuId(skuId)
                .orElseThrow(() -> new ErrorException(InventoryErrorCode.STOCK_NOT_FOUND));
        if (!sellerId.equals(stock.getSellerId())) {
            throw new ErrorException(CommonErrorCode.FORBIDDEN);
        }

        if (request.availableQty() != null) {
            stock.setAvailableQty(request.availableQty());
        }
        if (normalizedProductStatus != null) {
            stock.setProductStatus(normalizedProductStatus);
        }

        InventoryStockEntity saved = inventoryStockRepository.save(stock);
        inventoryRedisStockCacheService.cacheAvailableQty(saved.getSkuId(), saved.getAvailableQty());
        return InventoryStockResponse.from(saved);
    }

    @WriteTx
    public void deleteStocksByProductId(LoginUser currentUser, Long productId) {
        if (productId == null) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
        Long sellerId = requireSeller(currentUser);
        List<InventoryStockEntity> stocks = inventoryStockRepository.findAllByProductIdAndSellerId(productId, sellerId);
        if (stocks.isEmpty()) {
            return;
        }

        List<Long> skuIds = stocks.stream()
                .map(InventoryStockEntity::getSkuId)
                .toList();
        inventoryStockRepository.deleteAllInBatch(stocks);
        inventoryRedisStockCacheService.evictAvailableQtyBySkuIds(skuIds);
    }

    private void validateUpsertRequest(InventoryStockUpsertRequest request) {
        if (request == null
                || request.skuId() == null
                || request.availableQty() == null
                || request.availableQty() < 0
                || request.sellerId() == null
                || request.productId() == null
                || isBlank(request.productName())
                || request.productPrice() == null
                || request.productPrice().compareTo(BigDecimal.ZERO) < 0
                || isBlank(request.category())
                || normalizeProductStatus(request.productStatus()) == null
                || isBlank(request.size())
                || isBlank(request.color())
                || isBlank(request.mainImageUrl())) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
    }

    private Long requireSeller(LoginUser currentUser) {
        if (currentUser == null || currentUser.userId() == null || !currentUser.isSeller()) {
            throw new ErrorException(CommonErrorCode.FORBIDDEN);
        }
        return currentUser.userId();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeProductStatus(String productStatus) {
        return InventoryProductStatus.normalizeForPersist(productStatus);
    }
}

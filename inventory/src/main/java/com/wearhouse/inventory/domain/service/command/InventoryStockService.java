package com.wearhouse.inventory.domain.service.command;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.inventory.domain.dto.request.InventoryStockUpsertRequest;
import com.wearhouse.inventory.domain.dto.response.InventoryStockResponse;
import com.wearhouse.inventory.domain.entity.InventoryStockEntity;
import com.wearhouse.inventory.domain.exception.InventoryErrorCode;
import com.wearhouse.inventory.infra.jpa.repository.InventoryStockJpaRepository;
import com.wearhouse.inventory.infra.redis.InventoryRedisStockCacheService;
import org.springframework.stereotype.Service;
import com.wearhouse.common.global.transactional.WriteTx;

@Service
public class InventoryStockService {

    private final InventoryStockJpaRepository inventoryStockJpaRepository;
    private final InventoryRedisStockCacheService inventoryRedisStockCacheService;

    public InventoryStockService(
            InventoryStockJpaRepository inventoryStockJpaRepository,
            InventoryRedisStockCacheService inventoryRedisStockCacheService
    ) {
        this.inventoryStockJpaRepository = inventoryStockJpaRepository;
        this.inventoryRedisStockCacheService = inventoryRedisStockCacheService;
    }

    @WriteTx
    public InventoryStockResponse upsert(InventoryStockUpsertRequest request) {
        validate(request);

        InventoryStockEntity entity = inventoryStockJpaRepository.findBySkuId(request.skuId())
                .orElseGet(() -> InventoryStockEntity.create(
                        request.skuId(),
                        request.availableQty(),
                        request.sellerId(),
                        request.productId(),
                        request.productName(),
                        request.productPrice(),
                        request.category(),
                        request.size(),
                        request.color(),
                        request.mainImageUrl()
                ));

        entity.setAvailableQty(request.availableQty());
        entity.updateSnapshot(
                request.sellerId(),
                request.productId(),
                request.productName(),
                request.productPrice(),
                request.category(),
                request.size(),
                request.color(),
                request.mainImageUrl()
        );
        InventoryStockEntity saved = inventoryStockJpaRepository.save(entity);
        inventoryRedisStockCacheService.cacheAvailableQty(saved.getSkuId(), saved.getAvailableQty());
        return InventoryStockResponse.from(saved);
    }

    private void validate(InventoryStockUpsertRequest request) {
        if (request == null
                || request.skuId() == null
                || request.availableQty() == null
                || request.availableQty() < 0
                || request.sellerId() == null
                || request.productId() == null
                || request.productName() == null
                || request.productName().isBlank()
                || request.productPrice() == null
                || request.productPrice().signum() < 0
                || request.category() == null
                || request.category().isBlank()
                || request.size() == null
                || request.size().isBlank()
                || request.color() == null
                || request.color().isBlank()
                || request.mainImageUrl() == null
                || request.mainImageUrl().isBlank()) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
    }
}

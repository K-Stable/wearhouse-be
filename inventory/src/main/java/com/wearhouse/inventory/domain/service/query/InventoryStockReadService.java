package com.wearhouse.inventory.domain.service.query;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.inventory.domain.dto.response.InventoryStockResponse;
import com.wearhouse.inventory.domain.entity.InventoryStockEntity;
import com.wearhouse.inventory.domain.exception.InventoryErrorCode;
import com.wearhouse.inventory.infra.jpa.repository.InventoryStockJpaRepository;
import org.springframework.stereotype.Service;
import com.wearhouse.common.global.transactional.ReadTx;

@Service
public class InventoryStockReadService {

    private final InventoryStockJpaRepository inventoryStockJpaRepository;

    public InventoryStockReadService(InventoryStockJpaRepository inventoryStockJpaRepository) {
        this.inventoryStockJpaRepository = inventoryStockJpaRepository;
    }

    @ReadTx
    public InventoryStockResponse getBySkuId(Long skuId) {
        InventoryStockEntity entity = inventoryStockJpaRepository.findBySkuId(skuId)
                .orElseThrow(() -> new ErrorException(InventoryErrorCode.STOCK_NOT_FOUND));
        return InventoryStockResponse.from(entity);
    }
}

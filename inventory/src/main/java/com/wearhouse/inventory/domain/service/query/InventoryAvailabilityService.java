package com.wearhouse.inventory.domain.service.query;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.inventory.domain.dto.request.InventoryAvailabilityCheckRequest;
import com.wearhouse.inventory.domain.dto.request.InventoryAvailabilityCheckRequest.InventoryAvailabilityLineRequest;
import com.wearhouse.inventory.domain.dto.response.InventoryAvailabilityCheckResponse;
import com.wearhouse.inventory.domain.dto.response.InventoryAvailabilityCheckResponse.InventoryAvailabilityLineResponse;
import com.wearhouse.inventory.domain.exception.InventoryErrorCode;
import com.wearhouse.inventory.infra.redis.InventoryRedisStockCacheService;
import com.wearhouse.inventory.infra.redis.InventoryRedisStockCacheService.AtomicAvailabilityCheckResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.wearhouse.common.global.transactional.ReadTx;

@Service
public class InventoryAvailabilityService {

    private final InventoryRedisStockCacheService inventoryRedisStockCacheService;

    public InventoryAvailabilityService(InventoryRedisStockCacheService inventoryRedisStockCacheService) {
        this.inventoryRedisStockCacheService = inventoryRedisStockCacheService;
    }

    @ReadTx
    public InventoryAvailabilityCheckResponse check(InventoryAvailabilityCheckRequest request) {
        Map<Long, Integer> requestedBySku = aggregateRequestedBySku(request.items());
        AtomicAvailabilityCheckResult availabilityCheckResult =
                inventoryRedisStockCacheService.checkAvailabilityAtomically(requestedBySku);

        List<InventoryAvailabilityLineResponse> lines = new ArrayList<>();
        for (InventoryAvailabilityLineRequest item : request.items()) {
            Long skuId = resolveSkuId(item.productId(), item.optionId());
            int availableQty = availabilityCheckResult.availableBySku().getOrDefault(skuId, 0);
            lines.add(new InventoryAvailabilityLineResponse(
                    item.productId(),
                    item.optionId(),
                    skuId,
                    item.quantity(),
                    availableQty,
                    availableQty >= item.quantity()
            ));
        }

        return new InventoryAvailabilityCheckResponse(
                availabilityCheckResult.available(),
                lines
        );
    }

    private Map<Long, Integer> aggregateRequestedBySku(List<InventoryAvailabilityLineRequest> items) {
        Map<Long, Integer> requestedBySku = new LinkedHashMap<>();
        for (InventoryAvailabilityLineRequest item : items) {
            Long skuId = resolveSkuId(item.productId(), item.optionId());
            requestedBySku.merge(skuId, item.quantity(), Integer::sum);
        }
        return requestedBySku;
    }

    private Long resolveSkuId(Long productId, Long optionId) {
        Long skuId = optionId == null ? productId : optionId;
        if (skuId == null) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
        return skuId;
    }
}

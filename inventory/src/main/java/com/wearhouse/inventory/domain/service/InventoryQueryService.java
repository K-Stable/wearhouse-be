package com.wearhouse.inventory.domain.service;

import com.wearhouse.common.global.error.CommonErrorCode;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.inventory.domain.dto.request.InventoryAvailabilityCheckRequest;
import com.wearhouse.inventory.domain.dto.request.InventoryAvailabilityCheckRequest.InventoryAvailabilityLineRequest;
import com.wearhouse.inventory.domain.dto.response.InventoryAvailabilityCheckResponse;
import com.wearhouse.inventory.domain.dto.response.InventoryAvailabilityCheckResponse.InventoryAvailabilityLineResponse;
import com.wearhouse.inventory.domain.dto.response.InventoryStockResponse;
import com.wearhouse.inventory.domain.dto.response.SellerInventoryItemResponse;
import com.wearhouse.inventory.domain.entity.InventoryStockEntity;
import com.wearhouse.inventory.domain.exception.InventoryErrorCode;
import com.wearhouse.inventory.domain.model.InventoryProductStatus;
import com.wearhouse.inventory.domain.repository.InventoryStockRepository;
import com.wearhouse.inventory.infra.redis.InventoryRedisStockCacheService;
import com.wearhouse.inventory.infra.redis.InventoryRedisStockCacheService.AtomicAvailabilityCheckResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryQueryService {

    private static final int DEFAULT_SELLER_LIMIT = 50;
    private static final int MAX_SELLER_LIMIT = 200;

    private final InventoryStockRepository inventoryStockRepository;
    private final InventoryRedisStockCacheService inventoryRedisStockCacheService;

    @ReadTx
    public InventoryStockResponse findStockBySkuId(Long skuId) {
        InventoryStockEntity stock = inventoryStockRepository.findBySkuId(skuId)
                .orElseThrow(() -> new ErrorException(InventoryErrorCode.STOCK_NOT_FOUND));
        return InventoryStockResponse.from(stock);
    }

    @ReadTx
    public InventoryAvailabilityCheckResponse checkAvailability(InventoryAvailabilityCheckRequest request) {
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

        return new InventoryAvailabilityCheckResponse(availabilityCheckResult.available(), lines);
    }

    @ReadTx
    public List<SellerInventoryItemResponse> findSellerInventoryItems(
            LoginUser currentUser,
            String keyword,
            String status,
            int limit
    ) {
        Long sellerId = requireSeller(currentUser);
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeStatus(status);
        int normalizedLimit = normalizeLimit(limit);

        return inventoryStockRepository.findSellerInventoryItems(
                sellerId,
                normalizedKeyword,
                normalizedStatus,
                PageRequest.of(0, normalizedLimit)
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

    private Long requireSeller(LoginUser currentUser) {
        if (currentUser == null || currentUser.userId() == null || !currentUser.isSeller()) {
            throw new ErrorException(CommonErrorCode.FORBIDDEN);
        }
        return currentUser.userId();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_SELLER_LIMIT;
        }
        return Math.min(limit, MAX_SELLER_LIMIT);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        if (InventoryProductStatus.isAll(status)) {
            return null;
        }
        String normalized = InventoryProductStatus.normalizeForFilter(status);
        if (normalized == null) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
        return normalized;
    }

}

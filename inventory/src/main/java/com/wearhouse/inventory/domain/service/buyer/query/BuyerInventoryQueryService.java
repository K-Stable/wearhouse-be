package com.wearhouse.inventory.domain.service.buyer.query;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.inventory.domain.dto.request.InventoryAvailabilityCheckRequest;
import com.wearhouse.inventory.domain.dto.request.InventoryAvailabilityCheckRequest.InventoryAvailabilityLineRequest;
import com.wearhouse.inventory.domain.dto.request.InventoryOrderPreviewRequest;
import com.wearhouse.inventory.domain.dto.request.InventoryOrderPreviewRequest.InventoryOrderPreviewItemRequest;
import com.wearhouse.inventory.domain.dto.request.InventorySellerResolveRequest;
import com.wearhouse.inventory.domain.dto.response.InventoryAvailabilityCheckResponse;
import com.wearhouse.inventory.domain.dto.response.InventoryAvailabilityCheckResponse.InventoryAvailabilityLineResponse;
import com.wearhouse.inventory.domain.dto.response.InventoryOrderPreviewResponse;
import com.wearhouse.inventory.domain.dto.response.InventoryOrderPreviewResponse.InventoryOrderPreviewLineResponse;
import com.wearhouse.inventory.domain.dto.response.InventorySellerResolveResponse;
import com.wearhouse.inventory.domain.dto.response.InventorySellerResolveResponse.InventorySkuSellerLineResponse;
import com.wearhouse.inventory.domain.entity.InventoryStockEntity;
import com.wearhouse.inventory.domain.exception.InventoryErrorCode;
import com.wearhouse.inventory.domain.model.InventoryProductStatus;
import com.wearhouse.inventory.domain.repository.InventoryStockRepository;
import com.wearhouse.inventory.infra.redis.InventoryRedisStockCacheService;
import com.wearhouse.inventory.infra.redis.InventoryRedisStockCacheService.AtomicAvailabilityCheckResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuyerInventoryQueryService {

    private final InventoryStockRepository inventoryStockRepository;
    private final InventoryRedisStockCacheService inventoryRedisStockCacheService;

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
    public InventoryOrderPreviewResponse previewOrder(InventoryOrderPreviewRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }

        List<ResolvedPreviewItem> resolvedItems = new ArrayList<>(request.items().size());
        Map<Long, Integer> requestedBySku = new LinkedHashMap<>();
        for (InventoryOrderPreviewItemRequest item : request.items()) {
            if (item == null || item.productId() == null || item.quantity() == null || item.quantity() <= 0) {
                throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
            }

            InventoryStockEntity stock = inventoryStockRepository
                    .findFirstByProductIdAndOptionColorIgnoreCaseAndOptionSizeIgnoreCase(
                            item.productId(),
                            normalize(item.color()),
                            normalize(item.size())
                    )
                    .orElse(null);
            if (stock == null) {
                resolvedItems.add(ResolvedPreviewItem.unresolved(item));
                continue;
            }

            requestedBySku.merge(stock.getSkuId(), item.quantity(), Integer::sum);
            resolvedItems.add(ResolvedPreviewItem.resolved(item, stock));
        }

        Map<Long, Integer> availableBySku = requestedBySku.isEmpty()
                ? Collections.emptyMap()
                : inventoryRedisStockCacheService.checkAvailabilityAtomically(requestedBySku).availableBySku();

        List<InventoryOrderPreviewLineResponse> lines = new ArrayList<>(resolvedItems.size());
        for (ResolvedPreviewItem resolvedItem : resolvedItems) {
            if (resolvedItem.stock() == null) {
                lines.add(new InventoryOrderPreviewLineResponse(
                        resolvedItem.productId(),
                        null,
                        null,
                        null,
                        null,
                        resolvedItem.color(),
                        resolvedItem.size(),
                        null,
                        null,
                        resolvedItem.quantity(),
                        0,
                        false
                ));
                continue;
            }

            InventoryStockEntity stock = resolvedItem.stock();
            int availableQty = availableBySku.getOrDefault(stock.getSkuId(), 0);
            boolean releasedStatus = InventoryProductStatus.RELEASED.name().equalsIgnoreCase(stock.getProductStatus());
            boolean available = releasedStatus && availableQty >= resolvedItem.quantity();

            lines.add(new InventoryOrderPreviewLineResponse(
                    stock.getProductId(),
                    stock.getSkuId(),
                    stock.getSellerId(),
                    stock.getProductName(),
                    stock.getProductPrice(),
                    stock.getOptionColor(),
                    stock.getOptionSize(),
                    stock.getMainImageUrl(),
                    stock.getProductStatus(),
                    resolvedItem.quantity(),
                    availableQty,
                    available
            ));
        }
        return new InventoryOrderPreviewResponse(lines);
    }

    @ReadTx
    public InventorySellerResolveResponse resolveSellers(InventorySellerResolveRequest request) {
        if (request == null || request.skuIds() == null || request.skuIds().isEmpty()) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }

        List<Long> skuIds = request.skuIds().stream().distinct().toList();
        Map<Long, Long> sellerBySku = new LinkedHashMap<>();
        for (InventoryStockEntity stock : inventoryStockRepository.findAllBySkuIdIn(skuIds)) {
            sellerBySku.put(stock.getSkuId(), stock.getSellerId());
        }

        List<InventorySkuSellerLineResponse> items = new ArrayList<>(skuIds.size());
        for (Long skuId : skuIds) {
            items.add(new InventorySkuSellerLineResponse(skuId, sellerBySku.get(skuId)));
        }
        return new InventorySellerResolveResponse(items);
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

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }

    private record ResolvedPreviewItem(
            Long productId,
            String color,
            String size,
            Integer quantity,
            InventoryStockEntity stock
    ) {
        private static ResolvedPreviewItem resolved(InventoryOrderPreviewItemRequest request, InventoryStockEntity stock) {
            return new ResolvedPreviewItem(
                    request.productId(),
                    request.color(),
                    request.size(),
                    request.quantity(),
                    stock
            );
        }

        private static ResolvedPreviewItem unresolved(InventoryOrderPreviewItemRequest request) {
            return new ResolvedPreviewItem(
                    request.productId(),
                    request.color(),
                    request.size(),
                    request.quantity(),
                    null
            );
        }
    }
}

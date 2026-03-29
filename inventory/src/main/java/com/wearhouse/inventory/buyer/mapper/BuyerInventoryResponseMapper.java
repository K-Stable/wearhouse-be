package com.wearhouse.inventory.buyer.mapper;

import com.wearhouse.inventory.buyer.dto.response.InventoryAvailabilityCheckResponse;
import com.wearhouse.inventory.buyer.dto.response.InventoryAvailabilityCheckResponse.InventoryAvailabilityLineResponse;
import com.wearhouse.inventory.buyer.dto.response.InventoryOrderPreviewResponse;
import com.wearhouse.inventory.buyer.dto.response.InventoryOrderPreviewResponse.InventoryOrderPreviewLineResponse;
import com.wearhouse.inventory.domain.entity.InventoryStockEntity;
import com.wearhouse.inventory.internal.dto.response.InventorySellerResolveResponse;
import com.wearhouse.inventory.internal.dto.response.InventorySellerResolveResponse.InventorySkuSellerLineResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BuyerInventoryResponseMapper {

    public InventoryAvailabilityLineResponse toAvailabilityLineResponse(
            Long productId,
            Long optionId,
            Long skuId,
            Integer requestedQty,
            Integer availableQty
    ) {
        return new InventoryAvailabilityLineResponse(
                productId,
                optionId,
                skuId,
                requestedQty,
                availableQty,
                availableQty >= requestedQty
        );
    }

    public InventoryAvailabilityCheckResponse toAvailabilityCheckResponse(
            boolean available,
            List<InventoryAvailabilityLineResponse> items
    ) {
        return new InventoryAvailabilityCheckResponse(available, items);
    }

    public InventoryOrderPreviewLineResponse toUnresolvedPreviewLineResponse(
            Long productId,
            String color,
            String size,
            Integer requestedQuantity
    ) {
        return new InventoryOrderPreviewLineResponse(
                productId,
                null,
                null,
                null,
                null,
                color,
                size,
                null,
                null,
                requestedQuantity,
                0,
                false
        );
    }

    public InventoryOrderPreviewLineResponse toResolvedPreviewLineResponse(
            InventoryStockEntity stock,
            Integer requestedQuantity,
            Integer availableQuantity,
            boolean available
    ) {
        return new InventoryOrderPreviewLineResponse(
                stock.getProductId(),
                stock.getSkuId(),
                stock.getSellerId(),
                stock.getProductName(),
                stock.getProductPrice(),
                stock.getOptionColor(),
                stock.getOptionSize(),
                stock.getMainImageUrl(),
                stock.getProductStatus(),
                requestedQuantity,
                availableQuantity,
                available
        );
    }

    public InventoryOrderPreviewResponse toOrderPreviewResponse(List<InventoryOrderPreviewLineResponse> items) {
        return new InventoryOrderPreviewResponse(items);
    }

    public InventorySellerResolveResponse toSellerResolveResponse(List<Long> skuIds, Map<Long, Long> sellerBySku) {
        List<InventorySkuSellerLineResponse> items = new ArrayList<>(skuIds.size());
        for (Long skuId : skuIds) {
            items.add(new InventorySkuSellerLineResponse(skuId, sellerBySku.get(skuId)));
        }
        return new InventorySellerResolveResponse(items);
    }
}

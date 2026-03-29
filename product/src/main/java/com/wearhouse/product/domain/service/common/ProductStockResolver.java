package com.wearhouse.product.domain.service.common;

import com.wearhouse.product.domain.entity.ProductOptionEntity;
import com.wearhouse.product.infra.inventory.ProductInventoryClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductStockResolver {

    private final ProductInventoryClient productInventoryClient;

    public Map<Long, Integer> resolveOptionStocks(List<ProductOptionEntity> options) {
        Map<Long, Integer> stockByOptionId = new HashMap<>();
        if (options == null || options.isEmpty()) {
            return stockByOptionId;
        }

        for (ProductOptionEntity option : options) {
            if (option != null && option.getId() != null) {
                stockByOptionId.put(option.getId(), option.getStockQuantity());
            }
        }

        List<Long> optionIds = options.stream()
                .filter(Objects::nonNull)
                .map(ProductOptionEntity::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (optionIds.isEmpty()) {
            return stockByOptionId;
        }

        try {
            Map<Long, Integer> availableByOptionId = productInventoryClient.getAvailableQtyBulk(optionIds);
            for (Long optionId : optionIds) {
                Integer availableQty = availableByOptionId.get(optionId);
                if (availableQty != null) {
                    stockByOptionId.put(optionId, availableQty);
                }
            }
        } catch (RuntimeException ignored) {
            // inventory 조회 실패 시 product DB stock 값으로 응답한다.
        }
        return stockByOptionId;
    }
}

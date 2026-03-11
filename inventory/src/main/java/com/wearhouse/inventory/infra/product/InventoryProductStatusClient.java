package com.wearhouse.inventory.infra.product;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.infra.feign.product.ProductInternalFeignClient;
import com.wearhouse.common.infra.feign.product.dto.ProductSoldOutSyncRequest;
import feign.FeignException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryProductStatusClient {

    private final ProductInternalFeignClient productInternalFeignClient;

    public void markProductsSoldOut(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        try {
            ApiResponse<Void> response = productInternalFeignClient.markProductsSoldOut(
                    new ProductSoldOutSyncRequest(productIds)
            );
            if (response == null || !response.success()) {
                throw new IllegalStateException("product sold-out 동기화 응답이 유효하지 않습니다.");
            }
        } catch (FeignException exception) {
            throw new IllegalStateException("product sold-out 동기화 호출 실패: " + exception.status(), exception);
        }
    }
}

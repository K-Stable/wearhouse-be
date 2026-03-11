package com.wearhouse.common.infra.feign.product;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.infra.feign.product.dto.ProductSoldOutSyncRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "product-service", path = "/api/v1/internal/products")
public interface ProductInternalFeignClient {

    @PatchMapping("/status/sold-out")
    ApiResponse<Void> markProductsSoldOut(
            @RequestBody ProductSoldOutSyncRequest request
    );
}

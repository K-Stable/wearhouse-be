package com.wearhouse.common.infra.feign.inventory;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.infra.feign.inventory.dto.InventoryOrderPreviewRequest;
import com.wearhouse.common.infra.feign.inventory.dto.InventoryOrderPreviewResponse;
import com.wearhouse.common.infra.feign.inventory.dto.InventorySellerResolveRequest;
import com.wearhouse.common.infra.feign.inventory.dto.InventorySellerResolveResponse;
import com.wearhouse.common.infra.feign.inventory.dto.InventoryStockResponse;
import com.wearhouse.common.infra.feign.inventory.dto.InventoryStockUpsertRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "inventory-service", path = "/api/v1/internal/inventory")
public interface InventoryStockFeignClient {

    @PostMapping("/stocks")
    ApiResponse<InventoryStockResponse> upsertStock(
            @RequestHeader("X-Passport-User") String encodedUser,
            @RequestHeader("X-Passport-Sig") String signature,
            @RequestHeader("X-Passport-Ts") String timestamp,
            @RequestBody InventoryStockUpsertRequest request
    );

    @GetMapping("/stocks/{skuId}")
    ApiResponse<InventoryStockResponse> getStock(
            @RequestHeader("X-Passport-User") String encodedUser,
            @RequestHeader("X-Passport-Sig") String signature,
            @RequestHeader("X-Passport-Ts") String timestamp,
            @PathVariable("skuId") Long skuId
    );

    @DeleteMapping("/stocks/products/{productId}")
    ApiResponse<Void> deleteStocksByProductId(
            @RequestHeader("X-Passport-User") String encodedUser,
            @RequestHeader("X-Passport-Sig") String signature,
            @RequestHeader("X-Passport-Ts") String timestamp,
            @PathVariable("productId") Long productId
    );

    @PostMapping("/orders/preview")
    ApiResponse<InventoryOrderPreviewResponse> previewOrder(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody InventoryOrderPreviewRequest request
    );

    @PostMapping("/stocks/sellers/resolve")
    ApiResponse<InventorySellerResolveResponse> resolveSellers(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody InventorySellerResolveRequest request
    );
}

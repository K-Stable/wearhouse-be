package com.wearhouse.inventory.domain.controller;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.global.error.CommonErrorCode;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.security.current.LoginSeller;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.inventory.domain.dto.request.InventoryAvailabilityCheckRequest;
import com.wearhouse.inventory.domain.dto.request.InventoryOrderPreviewRequest;
import com.wearhouse.inventory.domain.dto.request.InventoryStockUpsertRequest;
import com.wearhouse.inventory.domain.dto.response.InventoryAvailabilityCheckResponse;
import com.wearhouse.inventory.domain.dto.response.InventoryOrderPreviewResponse;
import com.wearhouse.inventory.domain.dto.response.InventoryStockResponse;
import com.wearhouse.inventory.domain.response.InventorySuccessCode;
import com.wearhouse.inventory.domain.service.InventoryQueryService;
import com.wearhouse.inventory.domain.service.command.InventoryCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/inventory")
public class InventoryController {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final InventoryQueryService inventoryQueryService;
    private final InventoryCommandService inventoryCommandService;
    @Value("${wearhouse.inventory.internal.shared-secret:wearhouse-inventory-internal-secret}")
    private String internalSharedSecret;


    @PostMapping("/stocks")
    public ApiResponse<InventoryStockResponse> upsertStock(@Valid @RequestBody InventoryStockUpsertRequest request) {
        InventoryStockResponse response = inventoryCommandService.upsertStock(request);
        return ApiResponse.success(InventorySuccessCode.INVENTORY_CREATED, response);
    }

    @GetMapping("/stocks/{skuId}")
    public ApiResponse<InventoryStockResponse> getStock(@PathVariable Long skuId) {
        InventoryStockResponse response = inventoryQueryService.findStockBySkuId(skuId);
        return ApiResponse.success(InventorySuccessCode.INVENTORY_FETCHED, response);
    }

    @PostMapping("/stocks/availability/check")
    public InventoryAvailabilityCheckResponse checkAvailability(
            @Valid @RequestBody InventoryAvailabilityCheckRequest request
    ) {
        return inventoryQueryService.checkAvailability(request);
    }

    @PostMapping("/orders/preview")
    public ApiResponse<InventoryOrderPreviewResponse> previewOrder(
            @RequestHeader(name = INTERNAL_SECRET_HEADER, required = false) String headerSecret,
            @Valid @RequestBody InventoryOrderPreviewRequest request
    ) {
        requireInternalSecret(headerSecret);
        InventoryOrderPreviewResponse response = inventoryQueryService.previewOrder(request);
        return ApiResponse.success(response);
    }

    @DeleteMapping("/stocks/products/{productId}")
    public ApiResponse<Void> deleteStocksByProductId(
            @LoginSeller LoginUser currentUser,
            @PathVariable Long productId
    ) {
        inventoryCommandService.deleteStocksByProductId(currentUser, productId);
        return ApiResponse.success(InventorySuccessCode.INVENTORY_DELETED);
    }

    private void requireInternalSecret(String headerSecret) {
        if (!internalSharedSecret.equals(headerSecret)) {
            throw new ErrorException(CommonErrorCode.FORBIDDEN);
        }
    }
}

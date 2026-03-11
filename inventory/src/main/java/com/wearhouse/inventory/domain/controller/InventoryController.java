package com.wearhouse.inventory.domain.controller;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.security.current.LoginSeller;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.inventory.domain.dto.request.InventoryAvailabilityCheckRequest;
import com.wearhouse.inventory.domain.dto.request.InventoryStockUpsertRequest;
import com.wearhouse.inventory.domain.dto.response.InventoryAvailabilityCheckResponse;
import com.wearhouse.inventory.domain.dto.response.InventoryStockResponse;
import com.wearhouse.inventory.domain.response.InventorySuccessCode;
import com.wearhouse.inventory.domain.service.InventoryQueryService;
import com.wearhouse.inventory.domain.service.command.InventoryCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/inventory")
public class InventoryController {

    private final InventoryQueryService inventoryQueryService;
    private final InventoryCommandService inventoryCommandService;


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

    @DeleteMapping("/stocks/products/{productId}")
    public ApiResponse<Void> deleteStocksByProductId(
            @LoginSeller LoginUser currentUser,
            @PathVariable Long productId
    ) {
        inventoryCommandService.deleteStocksByProductId(currentUser, productId);
        return ApiResponse.success(InventorySuccessCode.INVENTORY_DELETED);
    }
}

package com.wearhouse.inventory.domain.controller;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.security.current.LoginSeller;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.inventory.domain.dto.request.InventoryStockUpdateRequest;
import com.wearhouse.inventory.domain.dto.request.InventoryStockUpsertRequest;
import com.wearhouse.inventory.domain.dto.response.InventoryStockResponse;
import com.wearhouse.inventory.domain.dto.response.SellerInventoryItemResponse;
import com.wearhouse.inventory.domain.response.InventorySuccessCode;
import com.wearhouse.inventory.domain.service.seller.command.SellerInventoryCommandService;
import com.wearhouse.inventory.domain.service.seller.query.SellerInventoryQueryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InventorySellerController {

    private final SellerInventoryQueryService sellerInventoryQueryService;
    private final SellerInventoryCommandService sellerInventoryCommandService;

    @PostMapping("/api/v1/internal/inventory/stocks")
    public ApiResponse<InventoryStockResponse> upsertStock(@Valid @RequestBody InventoryStockUpsertRequest request) {
        InventoryStockResponse response = sellerInventoryCommandService.upsertStock(request);
        return ApiResponse.success(InventorySuccessCode.INVENTORY_CREATED, response);
    }

    @GetMapping("/api/v1/internal/inventory/stocks/{skuId}")
    public ApiResponse<InventoryStockResponse> getStock(@PathVariable Long skuId) {
        InventoryStockResponse response = sellerInventoryQueryService.findStockBySkuId(skuId);
        return ApiResponse.success(InventorySuccessCode.INVENTORY_FETCHED, response);
    }

    @DeleteMapping("/api/v1/internal/inventory/stocks/products/{productId}")
    public ApiResponse<Void> deleteStocksByProductId(
            @LoginSeller LoginUser currentUser,
            @PathVariable Long productId
    ) {
        sellerInventoryCommandService.deleteStocksByProductId(currentUser, productId);
        return ApiResponse.success(InventorySuccessCode.INVENTORY_DELETED);
    }

    @GetMapping("/api/v1/seller/inventories")
    public ApiResponse<List<SellerInventoryItemResponse>> getSellerInventories(
            @LoginSeller LoginUser currentUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit
    ) {
        List<SellerInventoryItemResponse> response =
                sellerInventoryQueryService.findSellerInventoryItems(currentUser, keyword, status, limit);
        return ApiResponse.success(InventorySuccessCode.INVENTORY_LIST_FETCHED, response);
    }

    @GetMapping("/api/v1/seller/inventories/{skuId}")
    public ApiResponse<InventoryStockResponse> getSellerInventoryDetail(
            @LoginSeller LoginUser currentUser,
            @PathVariable Long skuId
    ) {
        InventoryStockResponse response = sellerInventoryQueryService.findSellerInventoryDetail(currentUser, skuId);
        return ApiResponse.success(InventorySuccessCode.INVENTORY_FETCHED, response);
    }

    @PatchMapping("/api/v1/seller/inventories/{skuId}")
    public ApiResponse<InventoryStockResponse> updateSellerInventory(
            @LoginSeller LoginUser currentUser,
            @PathVariable Long skuId,
            @Valid @RequestBody InventoryStockUpdateRequest request
    ) {
        InventoryStockResponse response = sellerInventoryCommandService.updateSellerInventory(currentUser, skuId, request);
        return ApiResponse.success(InventorySuccessCode.INVENTORY_UPDATED, response);
    }

    @DeleteMapping("/api/v1/seller/inventories/products/{productId}")
    public ApiResponse<Void> deleteSellerStocksByProductId(
            @LoginSeller LoginUser currentUser,
            @PathVariable Long productId
    ) {
        sellerInventoryCommandService.deleteStocksByProductId(currentUser, productId);
        return ApiResponse.success(InventorySuccessCode.INVENTORY_DELETED);
    }
}

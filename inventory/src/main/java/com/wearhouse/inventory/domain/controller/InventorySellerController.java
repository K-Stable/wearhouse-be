package com.wearhouse.inventory.domain.controller;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.security.current.LoginSeller;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.inventory.domain.dto.request.InventoryStockUpdateRequest;
import com.wearhouse.inventory.domain.dto.response.InventoryStockResponse;
import com.wearhouse.inventory.domain.dto.response.SellerInventoryItemResponse;
import com.wearhouse.inventory.domain.response.InventorySuccessCode;
import com.wearhouse.inventory.domain.service.InventoryQueryService;
import com.wearhouse.inventory.domain.service.command.InventoryCommandService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/seller/inventories")
public class InventorySellerController {

    private final InventoryQueryService inventoryQueryService;
    private final InventoryCommandService inventoryCommandService;

    @GetMapping
    public ApiResponse<List<SellerInventoryItemResponse>> getSellerInventories(
            @LoginSeller LoginUser currentUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit
    ) {
        List<SellerInventoryItemResponse> response =
                inventoryQueryService.findSellerInventoryItems(currentUser, keyword, status, limit);
        return ApiResponse.success(InventorySuccessCode.INVENTORY_LIST_FETCHED, response);
    }

    @PatchMapping("/{skuId}")
    public ApiResponse<InventoryStockResponse> updateSellerInventory(
            @LoginSeller LoginUser currentUser,
            @PathVariable Long skuId,
            @Valid @RequestBody InventoryStockUpdateRequest request
    ) {
        InventoryStockResponse response = inventoryCommandService.updateSellerInventory(currentUser, skuId, request);
        return ApiResponse.success(InventorySuccessCode.INVENTORY_UPDATED, response);
    }
}

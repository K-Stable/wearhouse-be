package com.wearhouse.inventory.domain.controller;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.security.current.LoginSeller;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.inventory.domain.dto.response.SellerInventoryItemResponse;
import com.wearhouse.inventory.domain.response.InventorySuccessCode;
import com.wearhouse.inventory.domain.service.query.InventorySellerQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/seller/inventories")
public class InventorySellerController {

    private final InventorySellerQueryService inventorySellerQueryService;

    @GetMapping
    public ApiResponse<List<SellerInventoryItemResponse>> getSellerInventories(
            @LoginSeller LoginUser currentUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit
    ) {
        List<SellerInventoryItemResponse> response =
                inventorySellerQueryService.getSellerInventories(currentUser, keyword, limit);
        return ApiResponse.success(InventorySuccessCode.INVENTORY_LIST_FETCHED, response);
    }
}

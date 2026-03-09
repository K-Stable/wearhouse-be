package com.wearhouse.inventory.domain.controller;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.global.response.SuccessCode;
import com.wearhouse.inventory.domain.dto.request.InventoryAvailabilityCheckRequest;
import com.wearhouse.inventory.domain.dto.request.InventoryStockUpsertRequest;
import com.wearhouse.inventory.domain.dto.response.InventoryAvailabilityCheckResponse;
import com.wearhouse.inventory.domain.dto.response.InventoryStockResponse;
import com.wearhouse.inventory.domain.response.InventorySuccessCode;
import com.wearhouse.inventory.domain.service.command.InventoryStockService;
import com.wearhouse.inventory.domain.service.query.InventoryAvailabilityService;
import com.wearhouse.inventory.domain.service.query.InventoryStockReadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.shaded.com.google.protobuf.Api;
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

    private final InventoryStockService stockService;
    private final InventoryStockReadService stockReadService;
    private final InventoryAvailabilityService availabilityService;


    @PostMapping("/stocks")
    public ApiResponse<InventoryStockResponse> upsertStock(@Valid @RequestBody InventoryStockUpsertRequest request) {
        InventoryStockResponse response = stockService.upsert(request);
        return ApiResponse.success(InventorySuccessCode.INVENTORY_CREATED,response);
    }

    @GetMapping("/stocks/{skuId}")
    public InventoryStockResponse getStock(@PathVariable Long skuId) {
        return stockReadService.getBySkuId(skuId);
    }

    @PostMapping("/stocks/availability/check")
    public InventoryAvailabilityCheckResponse checkAvailability(
            @Valid @RequestBody InventoryAvailabilityCheckRequest request
    ) {
        return availabilityService.check(request);
    }
}

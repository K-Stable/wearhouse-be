package com.wearhouse.inventory.buyer.controller;

import com.wearhouse.common.global.error.CommonErrorCode;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.inventory.buyer.dto.request.InventoryAvailabilityCheckRequest;
import com.wearhouse.inventory.buyer.dto.request.InventoryOrderPreviewRequest;
import com.wearhouse.inventory.internal.dto.request.InventorySellerResolveRequest;
import com.wearhouse.inventory.buyer.dto.response.InventoryAvailabilityCheckResponse;
import com.wearhouse.inventory.buyer.dto.response.InventoryOrderPreviewResponse;
import com.wearhouse.inventory.internal.dto.response.InventorySellerResolveResponse;
import com.wearhouse.inventory.buyer.service.BuyerInventoryQueryService;
import com.wearhouse.inventory.support.config.InventoryProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/inventory")
public class BuyerInventoryController {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final BuyerInventoryQueryService buyerInventoryQueryService;
    private final InventoryProperties inventoryProperties;

    @PostMapping("/stocks/availability/check")
    public InventoryAvailabilityCheckResponse checkAvailability(
            @Valid @RequestBody InventoryAvailabilityCheckRequest request
    ) {
        return buyerInventoryQueryService.checkAvailability(request);
    }

    @PostMapping("/orders/preview")
    public ApiResponse<InventoryOrderPreviewResponse> previewOrder(
            @RequestHeader(name = INTERNAL_SECRET_HEADER, required = false) String headerSecret,
            @Valid @RequestBody InventoryOrderPreviewRequest request
    ) {
        requireInternalSecret(headerSecret);
        InventoryOrderPreviewResponse response = buyerInventoryQueryService.previewOrder(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/stocks/sellers/resolve")
    public ApiResponse<InventorySellerResolveResponse> resolveSellers(
            @RequestHeader(name = INTERNAL_SECRET_HEADER, required = false) String headerSecret,
            @Valid @RequestBody InventorySellerResolveRequest request
    ) {
        requireInternalSecret(headerSecret);
        InventorySellerResolveResponse response = buyerInventoryQueryService.resolveSellers(request);
        return ApiResponse.success(response);
    }

    private void requireInternalSecret(String headerSecret) {
        if (!inventoryProperties.internal().sharedSecret().equals(headerSecret)) {
            throw new ErrorException(CommonErrorCode.FORBIDDEN);
        }
    }
}

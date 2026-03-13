package com.wearhouse.cart.domain.controller;

import com.wearhouse.cart.domain.dto.request.CartItemQuantityUpdateRequest;
import com.wearhouse.cart.domain.dto.request.CartItemUpsertRequest;
import com.wearhouse.cart.domain.dto.response.CartItemResponse;
import com.wearhouse.cart.domain.dto.response.CartItemsResponse;
import com.wearhouse.cart.domain.response.CartSuccessCode;
import com.wearhouse.cart.domain.service.command.CartCommandService;
import com.wearhouse.cart.domain.service.query.CartQueryService;
import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.security.current.LoginBuyer;
import com.wearhouse.common.security.current.LoginUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/buyer/carts/items")
public class CartController {

    private final CartCommandService cartCommandService;
    private final CartQueryService cartQueryService;

    @GetMapping
    public ApiResponse<CartItemsResponse> getCartItems(@LoginBuyer LoginUser currentUser) {
        CartItemsResponse response = cartQueryService.getCartItems(currentUser);
        return ApiResponse.success(CartSuccessCode.CART_ITEM_LIST_FETCHED, response);
    }

    @PostMapping
    public ApiResponse<List<CartItemResponse>> upsertCartItems(
            @LoginBuyer LoginUser currentUser,
            @Valid @RequestBody CartItemUpsertRequest request
    ) {
        List<CartItemResponse> response = cartCommandService.upsertCartItems(currentUser, request);
        return ApiResponse.success(CartSuccessCode.CART_ITEM_UPSERTED, response);
    }

    @PatchMapping("/{cartItemId}")
    public ApiResponse<CartItemResponse> updateCartItemQuantity(
            @LoginBuyer LoginUser currentUser,
            @PathVariable Long cartItemId,
            @Valid @RequestBody CartItemQuantityUpdateRequest request
    ) {
        CartItemResponse response = cartCommandService.updateQuantity(currentUser, cartItemId, request);
        return ApiResponse.success(CartSuccessCode.CART_ITEM_QUANTITY_UPDATED, response);
    }

    @DeleteMapping("/{cartItemId}")
    public ApiResponse<Void> deleteCartItem(
            @LoginBuyer LoginUser currentUser,
            @PathVariable Long cartItemId
    ) {
        cartCommandService.deleteCartItem(currentUser, cartItemId);
        return ApiResponse.success(CartSuccessCode.CART_ITEM_DELETED);
    }

    @DeleteMapping
    public ApiResponse<Void> clearCartItems(@LoginBuyer LoginUser currentUser) {
        cartCommandService.clearCartItems(currentUser);
        return ApiResponse.success(CartSuccessCode.CART_ITEMS_CLEARED);
    }
}

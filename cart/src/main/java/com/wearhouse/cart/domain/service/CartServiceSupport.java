package com.wearhouse.cart.domain.service;

import com.wearhouse.cart.domain.dto.response.CartItemResponse;
import com.wearhouse.cart.domain.entity.CartItemEntity;
import com.wearhouse.cart.domain.exception.CartErrorCode;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.security.current.LoginUser;
import java.math.BigDecimal;

public final class CartServiceSupport {

    private static final String BUYER_USER_TYPE = "BUYER";

    private CartServiceSupport() {
    }

    public static Long extractBuyerId(LoginUser currentUser) {
        if (currentUser == null || currentUser.userId() == null || !BUYER_USER_TYPE.equalsIgnoreCase(currentUser.userType())) {
            throw new ErrorException(CartErrorCode.FORBIDDEN_CART_ACCESS);
        }
        return currentUser.userId();
    }

    public static CartItemResponse toCartItemResponse(CartItemEntity cartItem) {
        BigDecimal subtotalPrice = cartItem.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
        return new CartItemResponse(
                cartItem.getId(),
                cartItem.getProductId(),
                cartItem.getOptionId(),
                cartItem.getProductName(),
                cartItem.getMainImageUrl(),
                cartItem.getSize(),
                cartItem.getColor(),
                cartItem.getPrice(),
                cartItem.getQuantity(),
                subtotalPrice
        );
    }
}

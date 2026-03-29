package com.wearhouse.cart.domain.service;

import com.wearhouse.cart.domain.exception.CartErrorCode;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.security.current.LoginUser;

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
}

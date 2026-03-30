package com.wearhouse.cart.domain.mapper;

import com.wearhouse.cart.domain.dto.response.BuyerCartItemResponse;
import com.wearhouse.cart.domain.entity.CartItemEntity;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class CartResponseMapper {

    public BuyerCartItemResponse toCartItemResponse(CartItemEntity cartItem) {
        BigDecimal subtotalPrice = cartItem.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
        return new BuyerCartItemResponse(
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

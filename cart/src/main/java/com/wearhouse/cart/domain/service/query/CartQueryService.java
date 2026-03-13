package com.wearhouse.cart.domain.service.query;

import com.wearhouse.cart.domain.dto.response.CartItemResponse;
import com.wearhouse.cart.domain.dto.response.CartItemsResponse;
import com.wearhouse.cart.domain.entity.CartItemEntity;
import com.wearhouse.cart.domain.repository.CartItemRepository;
import com.wearhouse.cart.domain.service.CartServiceSupport;
import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.common.security.current.LoginUser;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CartQueryService {

    private final CartItemRepository cartItemRepository;

    @ReadTx
    public CartItemsResponse getCartItems(LoginUser currentUser) {
        Long buyerId = CartServiceSupport.extractBuyerId(currentUser);
        List<CartItemEntity> cartItems = cartItemRepository.findAllByBuyerIdOrderByUpdatedAtDescIdDesc(buyerId);
        List<CartItemResponse> items = cartItems.stream()
                .map(CartServiceSupport::toCartItemResponse)
                .toList();

        BigDecimal totalPrice = items.stream()
                .map(CartItemResponse::subtotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartItemsResponse(totalPrice, items);
    }
}

package com.wearhouse.cart.domain.service.query;

import com.wearhouse.cart.domain.dto.response.BuyerCartItemResponse;
import com.wearhouse.cart.domain.dto.response.BuyerCartItemsResponse;
import com.wearhouse.cart.domain.entity.CartItemEntity;
import com.wearhouse.cart.domain.mapper.CartResponseMapper;
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
    private final CartResponseMapper cartResponseMapper;

    @ReadTx
    public BuyerCartItemsResponse getBuyerCartItems(LoginUser currentUser) {
        Long buyerId = CartServiceSupport.extractBuyerId(currentUser);
        List<CartItemEntity> cartItems = cartItemRepository.findAllByBuyerIdOrderByUpdatedAtDescIdDesc(buyerId);
        List<BuyerCartItemResponse> items = cartItems.stream()
                .map(cartResponseMapper::toCartItemResponse)
                .toList();

        BigDecimal totalPrice = items.stream()
                .map(BuyerCartItemResponse::subtotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new BuyerCartItemsResponse(totalPrice, items);
    }
}

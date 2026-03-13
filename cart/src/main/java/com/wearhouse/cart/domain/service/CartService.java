package com.wearhouse.cart.domain.service;

import com.wearhouse.cart.domain.dto.request.CartItemQuantityUpdateRequest;
import com.wearhouse.cart.domain.dto.request.CartItemUpsertRequest;
import com.wearhouse.cart.domain.dto.response.CartItemResponse;
import com.wearhouse.cart.domain.dto.response.CartItemsResponse;
import com.wearhouse.cart.domain.entity.CartItemEntity;
import com.wearhouse.cart.domain.exception.CartErrorCode;
import com.wearhouse.cart.domain.repository.CartItemRepository;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.common.security.current.LoginUser;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CartService {

    private static final String BUYER_USER_TYPE = "BUYER";

    private final CartItemRepository cartItemRepository;

    @ReadTx
    public CartItemsResponse getCartItems(LoginUser currentUser) {
        Long buyerId = extractBuyerId(currentUser);
        List<CartItemEntity> cartItems = cartItemRepository.findAllByBuyerIdOrderByUpdatedAtDescIdDesc(buyerId);
        List<CartItemResponse> items = cartItems.stream()
                .map(this::toCartItemResponse)
                .toList();

        BigDecimal totalPrice = items.stream()
                .map(CartItemResponse::subtotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartItemsResponse(totalPrice, items);
    }

    @WriteTx
    public List<CartItemResponse> upsertCartItems(LoginUser currentUser, CartItemUpsertRequest request) {
        Long buyerId = extractBuyerId(currentUser);
        List<CartItemUpsertRequest.CartOptionRequest> mergedItems = mergeOptionItems(request.items());
        return mergedItems.stream()
                .map(item -> upsertCartItem(buyerId, request, item))
                .toList();
    }

    @WriteTx
    public CartItemResponse updateQuantity(
            LoginUser currentUser,
            Long cartItemId,
            CartItemQuantityUpdateRequest request
    ) {
        Long buyerId = extractBuyerId(currentUser);
        CartItemEntity cartItem = cartItemRepository.findByIdAndBuyerId(cartItemId, buyerId)
                .orElseThrow(() -> new ErrorException(CartErrorCode.CART_ITEM_NOT_FOUND));
        cartItem.updateQuantity(request.quantity());
        return toCartItemResponse(cartItem);
    }

    @WriteTx
    public void deleteCartItem(LoginUser currentUser, Long cartItemId) {
        Long buyerId = extractBuyerId(currentUser);
        CartItemEntity cartItem = cartItemRepository.findByIdAndBuyerId(cartItemId, buyerId)
                .orElseThrow(() -> new ErrorException(CartErrorCode.CART_ITEM_NOT_FOUND));
        cartItemRepository.delete(cartItem);
    }

    @WriteTx
    public void clearCartItems(LoginUser currentUser) {
        Long buyerId = extractBuyerId(currentUser);
        cartItemRepository.deleteAllByBuyerId(buyerId);
    }

    private CartItemResponse toCartItemResponse(CartItemEntity cartItem) {
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

    private CartItemResponse upsertCartItem(
            Long buyerId,
            CartItemUpsertRequest request,
            CartItemUpsertRequest.CartOptionRequest item
    ) {
        CartItemEntity cartItem = cartItemRepository.findByBuyerIdAndProductIdAndOptionId(
                buyerId,
                request.productId(),
                item.optionId()
        ).orElseGet(() -> CartItemEntity.create(
                buyerId,
                request.productId(),
                item.optionId(),
                request.productName(),
                request.mainImageUrl(),
                item.size(),
                item.color(),
                request.price(),
                item.quantity()
        ));

        cartItem.updateSnapshot(
                request.productName(),
                request.mainImageUrl(),
                item.size(),
                item.color(),
                request.price()
        );
        if (cartItem.getId() == null) {
            cartItem.updateQuantity(item.quantity());
        } else {
            cartItem.updateQuantity(cartItem.getQuantity() + item.quantity());
        }

        CartItemEntity saved = cartItemRepository.save(cartItem);
        return toCartItemResponse(saved);
    }

    private List<CartItemUpsertRequest.CartOptionRequest> mergeOptionItems(
            List<CartItemUpsertRequest.CartOptionRequest> optionItems
    ) {
        Map<CartOptionKey, Integer> quantityByOption = new LinkedHashMap<>();
        for (CartItemUpsertRequest.CartOptionRequest optionItem : optionItems) {
            CartOptionKey key = new CartOptionKey(
                    optionItem.optionId(),
                    optionItem.size(),
                    optionItem.color()
            );
            quantityByOption.merge(key, optionItem.quantity(), Integer::sum);
        }
        return quantityByOption.entrySet().stream()
                .map(entry -> new CartItemUpsertRequest.CartOptionRequest(
                        entry.getKey().optionId(),
                        entry.getKey().size(),
                        entry.getKey().color(),
                        entry.getValue()
                ))
                .toList();
    }

    private record CartOptionKey(
            Long optionId,
            String size,
            String color
    ) {
    }

    private Long extractBuyerId(LoginUser currentUser) {
        if (currentUser == null || currentUser.userId() == null || !BUYER_USER_TYPE.equalsIgnoreCase(currentUser.userType())) {
            throw new ErrorException(CartErrorCode.FORBIDDEN_CART_ACCESS);
        }
        return currentUser.userId();
    }
}

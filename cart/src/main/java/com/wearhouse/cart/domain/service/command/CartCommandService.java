package com.wearhouse.cart.domain.service.command;

import com.wearhouse.cart.domain.dto.request.CartItemQuantityUpdateRequest;
import com.wearhouse.cart.domain.dto.request.CartItemUpsertRequest;
import com.wearhouse.cart.domain.dto.response.BuyerCartItemResponse;
import com.wearhouse.cart.domain.entity.CartItemEntity;
import com.wearhouse.cart.domain.exception.CartErrorCode;
import com.wearhouse.cart.domain.repository.CartItemRepository;
import com.wearhouse.cart.domain.service.CartServiceSupport;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.common.security.current.LoginUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CartCommandService {

    private final CartItemRepository cartItemRepository;

    @WriteTx
    public List<BuyerCartItemResponse> upsertCartItems(LoginUser currentUser, CartItemUpsertRequest request) {
        Long buyerId = CartServiceSupport.extractBuyerId(currentUser);
        List<CartItemUpsertRequest.CartOptionRequest> mergedItems = mergeOptionItems(request.items());
        return mergedItems.stream()
                .map(item -> upsertCartItem(buyerId, request, item))
                .toList();
    }

    @WriteTx
    public BuyerCartItemResponse updateQuantity(
            LoginUser currentUser,
            Long cartItemId,
            CartItemQuantityUpdateRequest request
    ) {
        Long buyerId = CartServiceSupport.extractBuyerId(currentUser);
        CartItemEntity cartItem = cartItemRepository.findByIdAndBuyerId(cartItemId, buyerId)
                .orElseThrow(() -> new ErrorException(CartErrorCode.CART_ITEM_NOT_FOUND));
        cartItem.updateQuantity(request.quantity());
        return CartServiceSupport.toCartItemResponse(cartItem);
    }

    @WriteTx
    public void deleteCartItem(LoginUser currentUser, Long cartItemId) {
        Long buyerId = CartServiceSupport.extractBuyerId(currentUser);
        CartItemEntity cartItem = cartItemRepository.findByIdAndBuyerId(cartItemId, buyerId)
                .orElseThrow(() -> new ErrorException(CartErrorCode.CART_ITEM_NOT_FOUND));
        cartItemRepository.delete(cartItem);
    }

    @WriteTx
    public void clearCartItems(LoginUser currentUser) {
        Long buyerId = CartServiceSupport.extractBuyerId(currentUser);
        cartItemRepository.deleteAllByBuyerId(buyerId);
    }

    private BuyerCartItemResponse upsertCartItem(
            Long buyerId,
            CartItemUpsertRequest request,
            CartItemUpsertRequest.CartOptionRequest item
    ) {
        CartItemEntity cartItem = cartItemRepository.findByBuyerIdAndProductIdAndOptionId(
                buyerId,
                request.productId(),
                item.optionId()
        ).orElseGet(() -> CartItemEntity.of(
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
        return CartServiceSupport.toCartItemResponse(saved);
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
}

package com.wearhouse.order.buyer.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.common.infra.feign.inventory.InventoryStockFeignClient;
import com.wearhouse.common.infra.feign.inventory.dto.InventoryOrderPreviewRequest;
import com.wearhouse.common.infra.feign.inventory.dto.InventoryOrderPreviewRequest.InventoryOrderPreviewItemRequest;
import com.wearhouse.common.infra.feign.inventory.dto.InventoryOrderPreviewResponse;
import com.wearhouse.common.infra.feign.inventory.dto.InventoryOrderPreviewResponse.InventoryOrderPreviewLine;
import com.wearhouse.common.infra.feign.userorder.UserOrderInfoFeignClient;
import com.wearhouse.common.infra.feign.userorder.dto.UserOrderPreviewInfoRequest;
import com.wearhouse.common.infra.feign.userorder.dto.UserOrderPreviewInfoResponse;
import com.wearhouse.order.buyer.dto.request.OrderPreviewRequest;
import com.wearhouse.order.buyer.dto.response.OrderDetailResponse;
import com.wearhouse.order.buyer.dto.response.OrderPreviewResponse;
import com.wearhouse.order.buyer.dto.response.OrderPreviewResponse.BuyerOrderPreviewInfo;
import com.wearhouse.order.buyer.dto.response.OrderPreviewResponse.OrderableItem;
import com.wearhouse.order.buyer.dto.response.OrderPreviewResponse.UnavailableItem;
import com.wearhouse.order.buyer.dto.response.OrderSummaryResponse;
import com.wearhouse.order.buyer.mapper.BuyerOrderResponseMapper;
import com.wearhouse.order.common.exception.OrderErrorCode;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.support.config.OrderInventoryInternalProperties;
import com.wearhouse.order.support.config.OrderUserInternalProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuyerOrderQueryService {

    private static final String NEXT_ACTION_VIEW_ORDER = "VIEW_ORDER";
    private static final String NEXT_ACTION_RETURN_TO_CHECKOUT = "RETURN_TO_CHECKOUT";
    private static final Set<OrderStatus> RETRYABLE_STATUSES = Set.of(
            OrderStatus.RESERVE_FAILED,
            OrderStatus.PAYMENT_FAILED
    );
    private static final BigDecimal DEFAULT_SHIPPING_FEE = new BigDecimal("3000");

    private final OrderRepository orderRepository;
    private final InventoryStockFeignClient inventoryStockFeignClient;
    private final UserOrderInfoFeignClient userOrderInfoFeignClient;
    private final OrderInventoryInternalProperties orderInventoryInternalProperties;
    private final OrderUserInternalProperties orderUserInternalProperties;
    private final BuyerOrderResponseMapper buyerOrderResponseMapper;

    @ReadTx
    public OrderDetailResponse getOrderDetail(Long buyerId, String orderNo) {
        OrderEntity order = orderRepository.findDetailByOrderNo(orderNo)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
        validateOwnedByBuyer(order, buyerId);

        boolean retryable = RETRYABLE_STATUSES.contains(order.getStatus());
        return buyerOrderResponseMapper.toOrderDetailResponse(
                order,
                retryable,
                retryable ? NEXT_ACTION_RETURN_TO_CHECKOUT : NEXT_ACTION_VIEW_ORDER,
                buyerOrderResponseMapper.toDetailItemResponses(order.getItems())
        );
    }

    @ReadTx
    public List<OrderSummaryResponse> getBuyerOrders(Long buyerId, int limit) {
        List<OrderEntity> orders = orderRepository.findByBuyerIdOrderByIdDesc(buyerId, PageRequest.of(0, limit));
        List<OrderSummaryResponse> responses = new ArrayList<>();
        for (OrderEntity order : orders) {
            responses.add(buyerOrderResponseMapper.toOrderSummaryResponse(order));
        }
        return responses;
    }

    @ReadTx
    public OrderPreviewResponse previewForBuyer(Long buyerId, OrderPreviewRequest request) {
        return preview(request, buyerId);
    }

    @ReadTx
    public OrderPreviewResponse previewForGuest(OrderPreviewRequest request) {
        return preview(request, null);
    }

    private OrderPreviewResponse preview(OrderPreviewRequest request, Long buyerId) {
        List<InventoryOrderPreviewItemRequest> previewItems = aggregatePreviewItems(request);
        InventoryOrderPreviewResponse inventoryResponse = callInventoryPreview(previewItems);

        List<OrderableItem> orderableItems = new ArrayList<>();
        List<UnavailableItem> unavailableItems = new ArrayList<>();
        for (InventoryOrderPreviewLine item : inventoryResponse.items()) {
            if (item.available()) {
                BigDecimal lineAmount = item.unitPrice().multiply(BigDecimal.valueOf(item.requestedQuantity()));
                orderableItems.add(buyerOrderResponseMapper.toOrderableItem(item, lineAmount));
                continue;
            }
            unavailableItems.add(buyerOrderResponseMapper.toUnavailableItem(item, resolveUnavailableReason(item)));
        }

        BigDecimal itemAmount = orderableItems.stream()
                .map(OrderableItem::lineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shippingFee = orderableItems.isEmpty() ? BigDecimal.ZERO : DEFAULT_SHIPPING_FEE;
        BigDecimal payAmount = itemAmount.add(shippingFee);

        boolean allSoldOut = orderableItems.isEmpty();
        boolean partialSoldOut = !allSoldOut && !unavailableItems.isEmpty();
        String message = resolveMessage(allSoldOut, partialSoldOut);

        return buyerOrderResponseMapper.toOrderPreviewResponse(
                orderableItems,
                unavailableItems,
                allSoldOut,
                partialSoldOut,
                message,
                itemAmount,
                shippingFee,
                payAmount,
                buyerId == null ? null : fetchBuyerPreviewInfo(buyerId)
        );
    }

    private List<InventoryOrderPreviewItemRequest> aggregatePreviewItems(OrderPreviewRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new ErrorException(OrderErrorCode.ORDER_ITEM_EMPTY);
        }

        Map<String, AggregatedPreviewItem> aggregated = new LinkedHashMap<>();
        for (OrderPreviewRequest.OrderPreviewItemRequest item : request.items()) {
            if (item == null || item.productId() == null || item.quantity() == null || item.quantity() <= 0) {
                throw new ErrorException(OrderErrorCode.ORDER_ITEM_EMPTY);
            }
            String normalizedColor = normalize(item.color());
            String normalizedSize = normalize(item.size());
            String key = item.productId() + ":" + normalizedColor.toLowerCase(Locale.ROOT) + ":" + normalizedSize.toLowerCase(Locale.ROOT);
            aggregated.compute(key, (unused, existing) -> {
                if (existing == null) {
                    return new AggregatedPreviewItem(item.productId(), normalizedColor, normalizedSize, item.quantity());
                }
                return new AggregatedPreviewItem(
                        existing.productId(),
                        existing.color(),
                        existing.size(),
                        existing.quantity() + item.quantity()
                );
            });
        }

        List<InventoryOrderPreviewItemRequest> items = new ArrayList<>(aggregated.size());
        for (AggregatedPreviewItem item : aggregated.values()) {
            items.add(new InventoryOrderPreviewItemRequest(
                    item.productId(),
                    item.color(),
                    item.size(),
                    item.quantity()
            ));
        }
        return items;
    }

    private InventoryOrderPreviewResponse callInventoryPreview(List<InventoryOrderPreviewItemRequest> items) {
        try {
            ApiResponse<InventoryOrderPreviewResponse> response = inventoryStockFeignClient.previewOrder(
                    orderInventoryInternalProperties.sharedSecret(),
                    new InventoryOrderPreviewRequest(items)
            );
            if (response == null || !response.success() || response.data() == null || response.data().items() == null) {
                throw new ErrorException(OrderErrorCode.INVENTORY_PREVIEW_INVALID_RESPONSE);
            }
            return response.data();
        } catch (ErrorException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ErrorException(OrderErrorCode.INVENTORY_PREVIEW_UNAVAILABLE);
        }
    }

    private BuyerOrderPreviewInfo fetchBuyerPreviewInfo(Long buyerId) {
        try {
            ApiResponse<UserOrderPreviewInfoResponse> response = userOrderInfoFeignClient.getBuyerPreviewInfo(
                    orderUserInternalProperties.sharedSecret(),
                    new UserOrderPreviewInfoRequest(buyerId)
            );
            if (response == null || !response.success() || response.data() == null) {
                throw new ErrorException(OrderErrorCode.USER_PREVIEW_INVALID_RESPONSE);
            }
            UserOrderPreviewInfoResponse data = response.data();
            return buyerOrderResponseMapper.toBuyerPreviewInfo(data);
        } catch (ErrorException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ErrorException(OrderErrorCode.USER_PREVIEW_UNAVAILABLE);
        }
    }

    private String resolveUnavailableReason(InventoryOrderPreviewLine item) {
        if (item.optionId() == null) {
            return "OPTION_NOT_FOUND";
        }
        if (item.availableQuantity() == null || item.availableQuantity() <= 0) {
            return "OUT_OF_STOCK";
        }
        if (item.productStatus() == null || !"RELEASED".equalsIgnoreCase(item.productStatus())) {
            return "PRODUCT_NOT_RELEASED";
        }
        return "INSUFFICIENT_STOCK";
    }

    private String resolveMessage(boolean allSoldOut, boolean partialSoldOut) {
        if (allSoldOut) {
            return "상품이 품절되었습니다.";
        }
        if (partialSoldOut) {
            return "품절된 상품이 있습니다. 계속하시겠습니까?";
        }
        return "주문 가능한 상품입니다.";
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private void validateOwnedByBuyer(OrderEntity order, Long buyerId) {
        if (order.getBuyerId() == null || !order.getBuyerId().equals(buyerId)) {
            throw new ErrorException(OrderErrorCode.ORDER_NOT_FOUND);
        }
    }

    private record AggregatedPreviewItem(
            Long productId,
            String color,
            String size,
            Integer quantity
    ) {
    }
}

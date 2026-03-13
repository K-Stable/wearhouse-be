package com.wearhouse.order.domain.service.query;

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
import com.wearhouse.order.domain.dto.request.OrderPreviewRequest;
import com.wearhouse.order.domain.dto.response.OrderPreviewResponse;
import com.wearhouse.order.domain.dto.response.OrderPreviewResponse.BuyerOrderPreviewInfo;
import com.wearhouse.order.domain.dto.response.OrderPreviewResponse.DefaultAddress;
import com.wearhouse.order.domain.dto.response.OrderPreviewResponse.OrderableItem;
import com.wearhouse.order.domain.dto.response.OrderPreviewResponse.UnavailableItem;
import com.wearhouse.order.domain.exception.OrderErrorCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderPreviewQueryService {

    private static final BigDecimal DEFAULT_SHIPPING_FEE = new BigDecimal("3000");

    private final InventoryStockFeignClient inventoryStockFeignClient;
    private final UserOrderInfoFeignClient userOrderInfoFeignClient;
    @Value("${wearhouse.inventory.internal.shared-secret:wearhouse-inventory-internal-secret}")
    private String inventoryInternalSharedSecret;
    @Value("${wearhouse.user.internal.shared-secret:wearhouse-user-internal-secret}")
    private String userInternalSharedSecret;

    @ReadTx
    public OrderPreviewResponse previewForBuyer(Long buyerId, OrderPreviewRequest request) {
        return preview(request, buyerId);
    }

    @ReadTx
    public OrderPreviewResponse previewForGuest(OrderPreviewRequest request) {
        return preview(request, null);
    }

    private OrderPreviewResponse preview(OrderPreviewRequest request, Long buyerId) {
        List<InventoryOrderPreviewItemRequest> previewItems = aggregateItems(request);
        InventoryOrderPreviewResponse inventoryResponse = callInventoryPreview(previewItems);

        List<OrderableItem> orderableItems = new ArrayList<>();
        List<UnavailableItem> unavailableItems = new ArrayList<>();
        for (InventoryOrderPreviewLine item : inventoryResponse.items()) {
            if (item.available()) {
                BigDecimal lineAmount = item.unitPrice().multiply(BigDecimal.valueOf(item.requestedQuantity()));
                orderableItems.add(new OrderableItem(
                        item.productId(),
                        item.optionId(),
                        item.sellerId(),
                        item.productName(),
                        item.color(),
                        item.size(),
                        item.mainImageUrl(),
                        item.productStatus(),
                        item.requestedQuantity(),
                        item.unitPrice(),
                        lineAmount
                ));
                continue;
            }
            unavailableItems.add(new UnavailableItem(
                    item.productId(),
                    item.color(),
                    item.size(),
                    item.requestedQuantity(),
                    item.availableQuantity(),
                    resolveUnavailableReason(item)
            ));
        }

        BigDecimal itemAmount = orderableItems.stream()
                .map(OrderableItem::lineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shippingFee = orderableItems.isEmpty() ? BigDecimal.ZERO : DEFAULT_SHIPPING_FEE;
        BigDecimal payAmount = itemAmount.add(shippingFee);

        boolean allSoldOut = orderableItems.isEmpty();
        boolean partialSoldOut = !allSoldOut && !unavailableItems.isEmpty();
        String message = resolveMessage(allSoldOut, partialSoldOut);

        return new OrderPreviewResponse(
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

    private List<InventoryOrderPreviewItemRequest> aggregateItems(OrderPreviewRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new ErrorException(OrderErrorCode.ORDER_ITEM_EMPTY);
        }

        Map<String, AggregatedItem> aggregated = new LinkedHashMap<>();
        for (OrderPreviewRequest.OrderPreviewItemRequest item : request.items()) {
            if (item == null || item.productId() == null || item.quantity() == null || item.quantity() <= 0) {
                throw new ErrorException(OrderErrorCode.ORDER_ITEM_EMPTY);
            }
            String normalizedColor = normalize(item.color());
            String normalizedSize = normalize(item.size());
            String key = item.productId() + ":" + normalizedColor.toLowerCase(Locale.ROOT) + ":" + normalizedSize.toLowerCase(Locale.ROOT);
            aggregated.compute(key, (unused, existing) -> {
                if (existing == null) {
                    return new AggregatedItem(item.productId(), normalizedColor, normalizedSize, item.quantity());
                }
                return new AggregatedItem(
                        existing.productId(),
                        existing.color(),
                        existing.size(),
                        existing.quantity() + item.quantity()
                );
            });
        }

        List<InventoryOrderPreviewItemRequest> items = new ArrayList<>(aggregated.size());
        for (AggregatedItem item : aggregated.values()) {
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
                    inventoryInternalSharedSecret,
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
                    userInternalSharedSecret,
                    new UserOrderPreviewInfoRequest(buyerId)
            );
            if (response == null || !response.success() || response.data() == null) {
                throw new ErrorException(OrderErrorCode.USER_PREVIEW_INVALID_RESPONSE);
            }
            UserOrderPreviewInfoResponse data = response.data();
            DefaultAddress defaultAddress = data.defaultAddress() == null ? null : new DefaultAddress(
                    data.defaultAddress().addressId(),
                    data.defaultAddress().label(),
                    data.defaultAddress().recipientName(),
                    data.defaultAddress().recipientPhone(),
                    data.defaultAddress().zipCode(),
                    data.defaultAddress().address1(),
                    data.defaultAddress().address2()
            );
            return new BuyerOrderPreviewInfo(data.point(), defaultAddress);
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

    private record AggregatedItem(
            Long productId,
            String color,
            String size,
            Integer quantity
    ) {
    }
}

package com.wearhouse.order.domain.service.query;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.common.infra.feign.inventory.InventoryStockFeignClient;
import com.wearhouse.common.infra.feign.inventory.dto.InventoryOrderPreviewRequest;
import com.wearhouse.common.infra.feign.inventory.dto.InventoryOrderPreviewRequest.InventoryOrderPreviewItemRequest;
import com.wearhouse.common.infra.feign.inventory.dto.InventoryOrderPreviewResponse;
import com.wearhouse.common.infra.feign.inventory.dto.InventoryOrderPreviewResponse.InventoryOrderPreviewLine;
import com.wearhouse.common.infra.feign.userorder.UserOrderInfoFeignClient;
import com.wearhouse.common.infra.feign.userorder.dto.UserOrderPreviewInfoRequest;
import com.wearhouse.common.infra.feign.userorder.dto.UserOrderPreviewInfoResponse;
import com.wearhouse.order.domain.dto.request.OrderPreviewRequest;
import com.wearhouse.order.domain.dto.response.OrderDetailResponse;
import com.wearhouse.order.domain.dto.response.OrderDetailResponse.OrderItemDetailResponse;
import com.wearhouse.order.domain.dto.response.OrderPreviewResponse;
import com.wearhouse.order.domain.dto.response.OrderPreviewResponse.BuyerOrderPreviewInfo;
import com.wearhouse.order.domain.dto.response.OrderPreviewResponse.DefaultAddress;
import com.wearhouse.order.domain.dto.response.OrderPreviewResponse.OrderableItem;
import com.wearhouse.order.domain.dto.response.OrderPreviewResponse.UnavailableItem;
import com.wearhouse.order.domain.dto.response.OrderSummaryResponse;
import com.wearhouse.order.domain.dto.response.SellerOrderListItemResponse;
import com.wearhouse.order.domain.dto.response.SellerOrderListPageResponse;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.entity.OrderItemEntity;
import com.wearhouse.order.domain.entity.OrderInfo;
import com.wearhouse.order.domain.exception.OrderErrorCode;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
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
    @Value("${wearhouse.inventory.internal.shared-secret:wearhouse-inventory-internal-secret}")
    private String inventoryInternalSharedSecret;
    @Value("${wearhouse.user.internal.shared-secret:wearhouse-user-internal-secret}")
    private String userInternalSharedSecret;

    @ReadTx
    public OrderDetailResponse getOrderDetail(String orderNo) {
        OrderEntity order = orderRepository.findDetailByOrderNo(orderNo)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));

        List<OrderItemDetailResponse> detailItems = mapDetailItems(order.getItems());
        OrderInfo info = order.getOrderInfo();
        boolean retryable = RETRYABLE_STATUSES.contains(order.getStatus());
        return OrderDetailResponse.builder()
                .orderNo(order.getOrderNo())
                .buyerId(order.getBuyerId())
                .status(order.getStatus().name())
                .failReasonCode(order.getFailReasonCode())
                .retryable(retryable)
                .nextAction(retryable ? NEXT_ACTION_RETURN_TO_CHECKOUT : NEXT_ACTION_VIEW_ORDER)
                .paymentMethod(info == null || info.getPaymentMethod() == null ? null : info.getPaymentMethod().name())
                .recipientName(info == null ? null : info.getRecipientName())
                .recipientPhone(info == null ? null : info.getRecipientPhone())
                .zipCode(info == null ? null : info.getZipCode())
                .address1(info == null ? null : info.getAddress1())
                .address2(info == null ? null : info.getAddress2())
                .deliveryRequest(info == null ? null : info.getDeliveryRequest())
                .itemAmount(order.getItemAmount())
                .shippingFee(order.getShippingFee())
                .discountAmount(order.getDiscountAmount())
                .pointUsedAmount(order.getPointUsedAmount())
                .payAmount(order.getTotalAmount())
                .orderedAt(order.getOrderedAt())
                .items(detailItems)
                .build();
    }

    @ReadTx
    public List<OrderSummaryResponse> getBuyerOrders(Long buyerId, int limit) {
        List<OrderEntity> orders = orderRepository.findByBuyerIdOrderByIdDesc(buyerId, PageRequest.of(0, limit));
        List<OrderSummaryResponse> responses = new ArrayList<>();
        for (OrderEntity order : orders) {
            responses.add(OrderSummaryResponse.builder()
                    .orderNo(order.getOrderNo())
                    .status(order.getStatus().name())
                    .payAmount(order.getTotalAmount())
                    .orderedAt(order.getOrderedAt())
                    .build());
        }
        return responses;
    }

    @ReadTx
    public SellerOrderListPageResponse getSellerOrders(
            LoginUser currentUser,
            String keyword,
            OrderStatus status,
            Integer page,
            Integer size
    ) {
        int pageNumber = resolvePage(page);
        int pageSize = resolveSize(size);
        Page<OrderEntity> orders = orderRepository.findOrdersForSellerDashboard(
                normalizeKeyword(keyword),
                status,
                PageRequest.of(pageNumber, pageSize)
        );

        List<SellerOrderListItemResponse> content = orders.getContent().stream()
                .map(this::toSellerOrderListItem)
                .toList();

        return new SellerOrderListPageResponse(
                content,
                orders.getNumber(),
                orders.getSize(),
                orders.getTotalElements(),
                orders.getTotalPages(),
                orders.hasNext(),
                orders.hasPrevious()
        );
    }

    @ReadTx
    public OrderPreviewResponse previewForBuyer(Long buyerId, OrderPreviewRequest request) {
        return preview(request, buyerId);
    }

    @ReadTx
    public OrderPreviewResponse previewForGuest(OrderPreviewRequest request) {
        return preview(request, null);
    }

    private List<OrderItemDetailResponse> mapDetailItems(List<OrderItemEntity> orderItems) {
        List<OrderItemDetailResponse> detailItems = new ArrayList<>();
        for (OrderItemEntity orderItem : orderItems) {
            detailItems.add(OrderItemDetailResponse.builder()
                    .productId(orderItem.getProductId())
                    .optionId(orderItem.getOptionId())
                    .productName(orderItem.getProductNameSnapshot())
                    .optionName(orderItem.getOptionNameSnapshot())
                    .unitPrice(orderItem.getUnitPrice())
                    .quantity(orderItem.getQuantity())
                    .lineAmount(orderItem.getLineAmount())
                    .status(orderItem.getStatus().name())
                    .build());
        }
        return detailItems;
    }

    private OrderPreviewResponse preview(OrderPreviewRequest request, Long buyerId) {
        List<InventoryOrderPreviewItemRequest> previewItems = aggregatePreviewItems(request);
        InventoryOrderPreviewResponse inventoryResponse = callInventoryPreview(previewItems);

        // Inventory preview 결과를 주문 가능/불가능 라인으로 분리해 프론트가 즉시 UI 분기할 수 있게 한다.
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

    private List<InventoryOrderPreviewItemRequest> aggregatePreviewItems(OrderPreviewRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new ErrorException(OrderErrorCode.ORDER_ITEM_EMPTY);
        }

        // 동일 상품/옵션 요청을 미리 합산해서 inventory 내부 조회/검증 횟수를 줄인다.
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

    private SellerOrderListItemResponse toSellerOrderListItem(OrderEntity order) {
        int totalQuantity = order.getItems().stream()
                .mapToInt(OrderItemEntity::getQuantity)
                .sum();
        OrderInfo info = order.getOrderInfo();
        return new SellerOrderListItemResponse(
                order.getOrderNo(),
                order.getBuyerId(),
                totalQuantity,
                info == null || info.getPaymentMethod() == null ? null : info.getPaymentMethod().name(),
                order.getOrderedAt() == null ? null : order.getOrderedAt().toLocalDate(),
                order.getStatus() == null ? null : order.getStatus().name()
        );
    }

    private int resolvePage(Integer page) {
        if (page == null || page < 0) {
            return 0;
        }
        return page;
    }

    private int resolveSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return size;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private record AggregatedPreviewItem(
            Long productId,
            String color,
            String size,
            Integer quantity
    ) {
    }
}

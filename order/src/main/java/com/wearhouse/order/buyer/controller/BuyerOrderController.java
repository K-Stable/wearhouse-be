package com.wearhouse.order.buyer.controller;

import com.wearhouse.common.security.current.LoginBuyer;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.order.buyer.dto.request.OrderCancelRequest;
import com.wearhouse.order.buyer.dto.request.OrderCreateRequest;
import com.wearhouse.order.buyer.dto.request.OrderPreviewRequest;
import com.wearhouse.order.buyer.dto.response.OrderCancelResponse;
import com.wearhouse.order.buyer.dto.response.OrderCreateResponse;
import com.wearhouse.order.buyer.dto.response.OrderDetailResponse;
import com.wearhouse.order.buyer.dto.response.OrderPreviewResponse;
import com.wearhouse.order.buyer.dto.response.OrderSummaryResponse;
import com.wearhouse.order.buyer.service.BuyerOrderCancelService;
import com.wearhouse.order.buyer.service.BuyerOrderCreateService;
import com.wearhouse.order.buyer.service.BuyerOrderQueryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BuyerOrderController {

    private final BuyerOrderCreateService buyerOrderCreateService;
    private final BuyerOrderCancelService buyerOrderCancelService;
    private final BuyerOrderQueryService buyerOrderQueryService;

    @PostMapping("/buyer/orders")
    public OrderCreateResponse createMemberOrder(
            @LoginBuyer LoginUser currentUser,
            @Valid @RequestBody OrderCreateRequest request
    ) {
        return buyerOrderCreateService.createMemberOrder(currentUser.userId(), request);
    }

    @PostMapping("/buyer/guest/orders")
    public OrderCreateResponse createGuestOrder(@Valid @RequestBody OrderCreateRequest request) {
        return buyerOrderCreateService.createGuestOrder(request);
    }

    @GetMapping("/buyer/orders/{orderNo}")
    public OrderDetailResponse getOrder(
            @LoginBuyer LoginUser currentUser,
            @PathVariable String orderNo
    ) {
        return buyerOrderQueryService.getOrderDetail(currentUser.userId(), orderNo);
    }

    @GetMapping("/buyer/orders")
    public List<OrderSummaryResponse> getBuyerOrders(
            @LoginBuyer LoginUser currentUser,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return buyerOrderQueryService.getBuyerOrders(currentUser.userId(), limit);
    }

    @PostMapping("/buyer/orders/{orderNo}/cancel")
    public OrderCancelResponse cancelOrder(
            @LoginBuyer LoginUser currentUser,
            @PathVariable String orderNo,
            @RequestBody(required = false) OrderCancelRequest request
    ) {
        return buyerOrderCancelService.cancelOrder(currentUser.userId(), orderNo, request);
    }

    @PostMapping("/buyer/orders/checkout")
    public OrderPreviewResponse buyerCheckout(
            @LoginBuyer LoginUser currentUser,
            @Valid @RequestBody OrderPreviewRequest request
    ) {
        return buyerOrderQueryService.previewForBuyer(currentUser.userId(), request);
    }

    @PostMapping("/buyer/guest/orders/checkout")
    public OrderPreviewResponse guestCheckout(@Valid @RequestBody OrderPreviewRequest request) {
        return buyerOrderQueryService.previewForGuest(request);
    }
}

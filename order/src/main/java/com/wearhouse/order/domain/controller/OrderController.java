package com.wearhouse.order.domain.controller;

import com.wearhouse.common.security.current.LoginBuyer;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.order.domain.dto.request.OrderCancelRequest;
import com.wearhouse.order.domain.dto.request.OrderCreateRequest;
import com.wearhouse.order.domain.dto.request.OrderPreviewRequest;
import com.wearhouse.order.domain.dto.response.OrderCancelResponse;
import com.wearhouse.order.domain.dto.response.OrderCreateResponse;
import com.wearhouse.order.domain.dto.response.OrderDetailResponse;
import com.wearhouse.order.domain.dto.response.OrderPreviewResponse;
import com.wearhouse.order.domain.dto.response.OrderSummaryResponse;
import com.wearhouse.order.domain.service.command.OrderCommandService;
import com.wearhouse.order.domain.service.command.OrderCheckoutOrchestrationService;
import com.wearhouse.order.domain.service.query.OrderQueryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final OrderCommandService orderCommandService;
    private final OrderCheckoutOrchestrationService orderCheckoutOrchestrationService;
    private final OrderQueryService orderQueryService;

    public OrderController(
            OrderCommandService orderCommandService,
            OrderCheckoutOrchestrationService orderCheckoutOrchestrationService,
            OrderQueryService orderQueryService
    ) {
        this.orderCommandService = orderCommandService;
        this.orderCheckoutOrchestrationService = orderCheckoutOrchestrationService;
        this.orderQueryService = orderQueryService;
    }

    @PostMapping("/orders")
    public OrderCreateResponse createOrder(@Valid @RequestBody OrderCreateRequest request) {
        return orderCheckoutOrchestrationService.createOrder(request);
    }

    @GetMapping("/orders/{orderNo}")
    public OrderDetailResponse getOrder(@PathVariable String orderNo) {
        return orderQueryService.getOrderDetail(orderNo);
    }

    @GetMapping("/orders")
    public List<OrderSummaryResponse> getBuyerOrders(
            @RequestParam Long buyerId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return orderQueryService.getBuyerOrders(buyerId, limit);
    }

    @PostMapping("/orders/{orderNo}/cancel")
    public OrderCancelResponse cancelOrder(@PathVariable String orderNo, @RequestBody(required = false) OrderCancelRequest request) {
        return orderCommandService.cancelOrder(orderNo, request);
    }

    @PostMapping("/buyer/orders/checkout")
    public OrderPreviewResponse buyerCheckout(
            @LoginBuyer LoginUser currentUser,
            @Valid @RequestBody OrderPreviewRequest request
    ) {
        return orderQueryService.previewForBuyer(currentUser.userId(), request);
    }

    @PostMapping("/buyer/orders/guest-checkout")
    public OrderPreviewResponse guestCheckout(@Valid @RequestBody OrderPreviewRequest request) {
        return orderQueryService.previewForGuest(request);
    }
}

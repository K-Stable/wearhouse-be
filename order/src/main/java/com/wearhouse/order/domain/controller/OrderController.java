package com.wearhouse.order.domain.controller;

import com.wearhouse.common.security.current.LoginBuyer;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.order.domain.dto.request.OrderCancelRequest;
import com.wearhouse.order.domain.dto.request.OrderCreateRequest;
import com.wearhouse.order.domain.dto.request.OrderPaymentConfirmRequest;
import com.wearhouse.order.domain.dto.request.OrderPreviewRequest;
import com.wearhouse.order.domain.dto.response.OrderCancelResponse;
import com.wearhouse.order.domain.dto.response.OrderCreateResponse;
import com.wearhouse.order.domain.dto.response.OrderDetailResponse;
import com.wearhouse.order.domain.dto.response.OrderPaymentConfirmResponse;
import com.wearhouse.order.domain.dto.response.OrderPreviewResponse;
import com.wearhouse.order.domain.dto.response.OrderSummaryResponse;
import com.wearhouse.order.domain.service.command.OrderCommandService;
import com.wearhouse.order.domain.service.command.OrderCheckoutOrchestrationService;
import com.wearhouse.order.domain.service.query.OrderQueryService;
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
public class OrderController {

    private final OrderCommandService orderCommandService;
    private final OrderCheckoutOrchestrationService orderCheckoutOrchestrationService;
    private final OrderQueryService orderQueryService;



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

    @PostMapping("/buyer/orders/{orderNo}/payments/confirm")
    public OrderPaymentConfirmResponse confirmStablepayPayment(
            @LoginBuyer LoginUser currentUser,
            @PathVariable String orderNo,
            @Valid @RequestBody OrderPaymentConfirmRequest request
    ) {
        return orderCheckoutOrchestrationService.confirmStablepayPayment(currentUser.userId(), orderNo, request);
    }
}

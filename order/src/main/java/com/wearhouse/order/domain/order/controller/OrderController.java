package com.wearhouse.order.domain.order.controller;

import com.wearhouse.order.domain.order.dto.request.OrderCancelRequest;
import com.wearhouse.order.domain.order.dto.request.OrderCreateRequest;
import com.wearhouse.order.domain.order.dto.response.OrderCancelResponse;
import com.wearhouse.order.domain.order.dto.response.OrderCreateResponse;
import com.wearhouse.order.domain.order.dto.response.OrderDetailResponse;
import com.wearhouse.order.domain.order.dto.response.OrderSummaryResponse;
import com.wearhouse.order.domain.order.service.command.OrderCommandService;
import com.wearhouse.order.domain.order.service.query.OrderQueryService;
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
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderCommandService orderCommandService;
    private final OrderQueryService orderQueryService;

    public OrderController(OrderCommandService orderCommandService, OrderQueryService orderQueryService) {
        this.orderCommandService = orderCommandService;
        this.orderQueryService = orderQueryService;
    }

    @PostMapping
    public OrderCreateResponse createOrder(@Valid @RequestBody OrderCreateRequest request) {
        return orderCommandService.createOrder(request);
    }

    @GetMapping("/{orderNo}")
    public OrderDetailResponse getOrder(@PathVariable String orderNo) {
        return orderQueryService.getOrderDetail(orderNo);
    }

    @GetMapping
    public List<OrderSummaryResponse> getBuyerOrders(
            @RequestParam Long buyerId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return orderQueryService.getBuyerOrders(buyerId, limit);
    }

    @PostMapping("/{orderNo}/cancel")
    public OrderCancelResponse cancelOrder(@PathVariable String orderNo, @RequestBody(required = false) OrderCancelRequest request) {
        return orderCommandService.cancelOrder(orderNo, request);
    }
}

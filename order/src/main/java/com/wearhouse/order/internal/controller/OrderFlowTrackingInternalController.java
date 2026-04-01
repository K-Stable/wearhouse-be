package com.wearhouse.order.internal.controller;

import com.wearhouse.order.internal.dto.response.OrderFlowTrackingResponse;
import com.wearhouse.order.internal.service.OrderFlowTrackingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/orders")
@RequiredArgsConstructor
public class OrderFlowTrackingInternalController {

    private final OrderFlowTrackingQueryService orderFlowTrackingQueryService;

    @GetMapping("/{orderNo}/tracking")
    public OrderFlowTrackingResponse getOrderFlowTracking(@PathVariable String orderNo) {
        return orderFlowTrackingQueryService.getByOrderNo(orderNo);
    }
}

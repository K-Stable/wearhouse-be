package com.wearhouse.order.seller.controller;

import com.wearhouse.common.security.current.LoginSeller;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.order.seller.dto.response.SellerOrderListPageResponse;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.seller.service.SellerOrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SellerOrderController {

    private final SellerOrderQueryService sellerOrderQueryService;

    @GetMapping("/seller/orders")
    public SellerOrderListPageResponse getSellerOrders(
            @LoginSeller LoginUser currentUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size
    ) {
        return sellerOrderQueryService.getSellerOrders(currentUser, keyword, status, page, size);
    }
}

package com.wearhouse.order.buyer.controller;

import com.wearhouse.common.security.current.LoginBuyer;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.order.buyer.dto.request.OrderPaymentConfirmRequest;
import com.wearhouse.order.buyer.dto.response.OrderPaymentConfirmResponse;
import com.wearhouse.order.buyer.dto.response.OrderPaymentPrepareResponse;
import com.wearhouse.order.buyer.service.BuyerOrderPaymentConfirmService;
import com.wearhouse.order.buyer.service.BuyerOrderPaymentPrepareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BuyerOrderPaymentController {

    private final BuyerOrderPaymentConfirmService buyerOrderPaymentConfirmService;
    private final BuyerOrderPaymentPrepareService buyerOrderPaymentPrepareService;

    @PostMapping("/buyer/orders/{orderNo}/payments/confirm")
    public OrderPaymentConfirmResponse confirmPayment(
            @LoginBuyer LoginUser currentUser,
            @PathVariable String orderNo,
            @Valid @RequestBody OrderPaymentConfirmRequest request
    ) {
        return buyerOrderPaymentConfirmService.confirmPayment(currentUser.userId(), orderNo, request);
    }

    @PostMapping("/buyer/orders/{orderNo}/payments/prepare")
    public OrderPaymentPrepareResponse preparePayment(
            @LoginBuyer LoginUser currentUser,
            @PathVariable String orderNo,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return buyerOrderPaymentPrepareService.preparePayment(currentUser.userId(), orderNo, idempotencyKey);
    }
}

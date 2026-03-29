package com.wearhouse.payment.internal.controller;

import com.wearhouse.payment.internal.dto.request.PaymentConfirmRequest;
import com.wearhouse.payment.internal.dto.request.WalletPrepareRequest;
import com.wearhouse.payment.internal.dto.response.PaymentConfirmResponse;
import com.wearhouse.payment.internal.dto.response.WalletPrepareResponse;
import com.wearhouse.payment.internal.service.PaymentInternalConfirmService;
import com.wearhouse.payment.internal.service.PaymentInternalPrepareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/payments")
public class PaymentInternalController {

    private final PaymentInternalPrepareService paymentInternalPrepareService;
    private final PaymentInternalConfirmService paymentInternalConfirmService;

    @PostMapping("/confirm")
    public PaymentConfirmResponse confirmPayment(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @Valid @RequestBody PaymentConfirmRequest request
    ) {
        return paymentInternalConfirmService.confirm(request, internalSecret);
    }

    @PostMapping("/prepare")
    public WalletPrepareResponse walletPrepare(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @Valid @RequestBody WalletPrepareRequest request
    ) {
        return paymentInternalPrepareService.prepare(request, internalSecret);
    }
}

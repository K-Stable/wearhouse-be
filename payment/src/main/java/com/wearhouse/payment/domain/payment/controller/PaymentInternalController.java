package com.wearhouse.payment.domain.payment.controller;

import com.wearhouse.payment.domain.payment.dto.request.PaymentConfirmRequest;
import com.wearhouse.payment.domain.payment.dto.request.PaymentPrepareRequest;
import com.wearhouse.payment.domain.payment.dto.response.PaymentConfirmResponse;
import com.wearhouse.payment.domain.payment.dto.response.PaymentPrepareResponse;
import com.wearhouse.payment.domain.payment.service.command.PaymentCommandService;
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

    private final PaymentCommandService paymentCommandService;

    @PostMapping("/confirm")
    public PaymentConfirmResponse confirmPayment(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @Valid @RequestBody PaymentConfirmRequest request
    ) {
        return paymentCommandService.confirmStablepayPayment(request, internalSecret);
    }

    @PostMapping("/prepare")
    public PaymentPrepareResponse preparePayment(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @Valid @RequestBody PaymentPrepareRequest request
    ) {
        return paymentCommandService.prepareStablepayPayment(request, internalSecret);
    }
}

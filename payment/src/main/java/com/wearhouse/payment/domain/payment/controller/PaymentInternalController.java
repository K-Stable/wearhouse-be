package com.wearhouse.payment.domain.payment.controller;

import com.wearhouse.payment.domain.payment.dto.request.StablepaySessionPrepareRequest;
import com.wearhouse.payment.domain.payment.dto.response.StablepaySessionPrepareResponse;
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

    @PostMapping("/stablepay/session")
    public StablepaySessionPrepareResponse prepareStablepaySession(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @Valid @RequestBody StablepaySessionPrepareRequest request
    ) {
        return paymentCommandService.prepareStablepaySession(request, internalSecret);
    }
}

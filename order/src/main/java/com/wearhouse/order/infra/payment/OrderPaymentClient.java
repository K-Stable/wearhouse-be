package com.wearhouse.order.infra.payment;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.order.infra.payment.dto.PaymentConfirmInternalRequest;
import com.wearhouse.order.infra.payment.dto.PaymentConfirmInternalResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "payment-service", path = "/api/v1/internal/payments")
public interface OrderPaymentClient {

    @PostMapping("/confirm")
    ApiResponse<PaymentConfirmInternalResponse> confirmStablepayPayment(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody PaymentConfirmInternalRequest request
    );
}

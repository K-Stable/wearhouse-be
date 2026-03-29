package com.wearhouse.order.paymentintegration.client;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.order.paymentintegration.dto.request.PaymentConfirmInternalRequest;
import com.wearhouse.order.paymentintegration.dto.response.PaymentConfirmInternalResponse;
import com.wearhouse.order.paymentintegration.dto.request.PaymentPrepareInternalRequest;
import com.wearhouse.order.paymentintegration.dto.response.PaymentPrepareInternalResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "payment-service", path = "/api/v1/internal/payments")
public interface OrderPaymentIntegrationClient {

    @PostMapping("/confirm")
    ApiResponse<PaymentConfirmInternalResponse> confirmStablepayPayment(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody PaymentConfirmInternalRequest request
    );

    @PostMapping("/prepare")
    ApiResponse<PaymentPrepareInternalResponse> prepareStablepayPayment(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody PaymentPrepareInternalRequest request
    );
}

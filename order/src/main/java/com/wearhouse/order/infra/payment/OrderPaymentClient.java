package com.wearhouse.order.infra.payment;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.order.infra.payment.dto.StablepaySessionPrepareRequest;
import com.wearhouse.order.infra.payment.dto.StablepaySessionPrepareResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "payment-service", path = "/api/v1/internal/payments")
public interface OrderPaymentClient {

    @PostMapping("/stablepay/session")
    ApiResponse<StablepaySessionPrepareResponse> prepareStablepaySession(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody StablepaySessionPrepareRequest request
    );
}

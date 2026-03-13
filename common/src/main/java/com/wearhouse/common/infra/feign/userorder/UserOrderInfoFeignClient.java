package com.wearhouse.common.infra.feign.userorder;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.infra.feign.userorder.dto.UserOrderPreviewInfoRequest;
import com.wearhouse.common.infra.feign.userorder.dto.UserOrderPreviewInfoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        contextId = "userOrderInfoFeignClient",
        name = "user-service",
        path = "/api/v1/internal/users/order-preview"
)
public interface UserOrderInfoFeignClient {

    @PostMapping("/buyers")
    ApiResponse<UserOrderPreviewInfoResponse> getBuyerPreviewInfo(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody UserOrderPreviewInfoRequest request
    );
}

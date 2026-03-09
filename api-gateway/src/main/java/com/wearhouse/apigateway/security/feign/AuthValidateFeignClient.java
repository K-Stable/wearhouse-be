package com.wearhouse.apigateway.security.feign;

import com.wearhouse.apigateway.security.dto.AuthValidateRequest;
import com.wearhouse.apigateway.security.dto.AuthValidateResponse;
import com.wearhouse.common.global.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "auth-service", path = "/api/v1/internal/auth")
public interface AuthValidateFeignClient {

    @PostMapping("/validate")
    ApiResponse<AuthValidateResponse> validate(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody AuthValidateRequest request
    );
}

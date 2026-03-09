package com.wearhouse.user.infra.feign;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.user.infra.auth.dto.AuthInternalSignupRequest;
import com.wearhouse.user.infra.auth.dto.AuthInternalSignupResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "auth-service")
public interface AuthSignupFeignClient {

    @PostMapping("/api/v1/internal/auth/buyers/signup")
    ApiResponse<AuthInternalSignupResponse> signupBuyer(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody AuthInternalSignupRequest request
    );

    @PostMapping("/api/v1/internal/auth/sellers/signup")
    ApiResponse<AuthInternalSignupResponse> signupSeller(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody AuthInternalSignupRequest request
    );
}

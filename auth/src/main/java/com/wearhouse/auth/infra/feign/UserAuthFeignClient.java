package com.wearhouse.auth.infra.feign;

import com.wearhouse.auth.infra.feign.dto.UserAuthByEmailRequest;
import com.wearhouse.auth.infra.feign.dto.UserAuthByIdRequest;
import com.wearhouse.auth.infra.feign.dto.UserAuthAccountResponse;
import com.wearhouse.auth.infra.feign.dto.UserAuthSignupRequest;
import com.wearhouse.common.global.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "user-service", path = "/api/v1/internal/users/auth")
public interface UserAuthFeignClient {

    @PostMapping("/signup")
    ApiResponse<UserAuthAccountResponse> signup(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody UserAuthSignupRequest request
    );

    @PostMapping("/by-email")
    ApiResponse<UserAuthAccountResponse> findByEmail(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody UserAuthByEmailRequest request
    );

    @PostMapping("/by-id")
    ApiResponse<UserAuthAccountResponse> findById(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody UserAuthByIdRequest request
    );
}

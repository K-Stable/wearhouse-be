package com.wearhouse.common.infra.feign.userauth;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.infra.feign.userauth.dto.UserAuthAccountResponse;
import com.wearhouse.common.infra.feign.userauth.dto.UserAuthByIdRequest;
import com.wearhouse.common.infra.feign.userauth.dto.UserAuthByLoginIdRequest;
import com.wearhouse.common.infra.feign.userauth.dto.UserAuthSignupRequest;
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

    @PostMapping("/by-login-id")
    ApiResponse<UserAuthAccountResponse> findByLoginId(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody UserAuthByLoginIdRequest request
    );

    @PostMapping("/by-id")
    ApiResponse<UserAuthAccountResponse> findById(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody UserAuthByIdRequest request
    );
}

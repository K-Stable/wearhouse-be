package com.wearhouse.user.seller.controller;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.support.email.EmailVerificationService;
import com.wearhouse.user.domain.dto.request.EmailCodeSendRequest;
import com.wearhouse.user.domain.dto.request.EmailCodeVerifyRequest;
import com.wearhouse.user.domain.dto.request.SellerSignupRequest;
import com.wearhouse.user.domain.dto.response.LoginIdAvailabilityResponse;
import com.wearhouse.user.domain.exception.UserErrorCode;
import com.wearhouse.user.domain.response.UserSuccessCode;
import com.wearhouse.user.seller.service.SellerUserCommandService;
import com.wearhouse.user.seller.service.SellerUserQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/sellers")
@RequiredArgsConstructor
public class SellerUserController {

    private final SellerUserCommandService sellerUserCommandService;
    private final SellerUserQueryService sellerUserQueryService;
    private final EmailVerificationService emailVerificationService;

    @GetMapping("/login-id/availability")
    public ApiResponse<LoginIdAvailabilityResponse> checkLoginIdAvailability(@RequestParam String loginId) {
        LoginIdAvailabilityResponse response = new LoginIdAvailabilityResponse(sellerUserQueryService.isLoginIdAvailable(loginId));
        return ApiResponse.success(UserSuccessCode.SELLER_LOGIN_ID_AVAILABILITY_CHECKED, response);
    }

    @PostMapping("/email-code/send")
    public ApiResponse<Void> sendEmailCode(@Valid @RequestBody EmailCodeSendRequest request) {
        emailVerificationService.sendCode(request.email());
        return ApiResponse.success(UserSuccessCode.SELLER_EMAIL_CODE_SENT);
    }

    @PostMapping("/email-code/verify")
    public ApiResponse<Void> verifyEmailCode(@Valid @RequestBody EmailCodeVerifyRequest request) {
        boolean verified = emailVerificationService.verifyCodeAndMarkVerified(request.email(), request.code());
        if (!verified) {
            throw new ErrorException(UserErrorCode.EMAIL_VERIFICATION_CODE_INVALID);
        }
        return ApiResponse.success(UserSuccessCode.SELLER_EMAIL_CODE_VERIFIED);
    }

    @PostMapping("/signup")
    public ApiResponse<Void> signupSeller(@Valid @RequestBody SellerSignupRequest request) {
        sellerUserCommandService.signUpSeller(request);
        return ApiResponse.success(UserSuccessCode.SELLER_SIGNUP_COMPLETED);
    }
}

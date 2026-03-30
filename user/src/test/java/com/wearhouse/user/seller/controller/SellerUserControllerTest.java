package com.wearhouse.user.seller.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerUserControllerTest {

    @Mock
    private SellerUserCommandService sellerUserCommandService;

    @Mock
    private SellerUserQueryService sellerUserQueryService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private SellerUserController sellerUserController;

    @Test
    void checkLoginIdAvailabilityShouldReturnWrappedResponse() {
        when(sellerUserQueryService.isLoginIdAvailable("seller01")).thenReturn(true);

        ApiResponse<LoginIdAvailabilityResponse> response = sellerUserController.checkLoginIdAvailability("seller01");

        assertTrue(response.success());
        assertEquals(UserSuccessCode.SELLER_LOGIN_ID_AVAILABILITY_CHECKED.code(), response.code());
        assertEquals(true, response.data().available());
    }

    @Test
    void sendEmailCodeShouldReturnWrappedResponse() {
        ApiResponse<Void> response = sellerUserController.sendEmailCode(new EmailCodeSendRequest("seller@example.com"));

        verify(emailVerificationService).sendCode("seller@example.com");
        assertTrue(response.success());
        assertEquals(UserSuccessCode.SELLER_EMAIL_CODE_SENT.code(), response.code());
        assertNull(response.data());
    }

    @Test
    void verifyEmailCodeShouldThrowWhenCodeIsInvalid() {
        when(emailVerificationService.verifyCodeAndMarkVerified("seller@example.com", "123456")).thenReturn(false);

        ErrorException exception = assertThrows(
                ErrorException.class,
                () -> sellerUserController.verifyEmailCode(new EmailCodeVerifyRequest("seller@example.com", "123456"))
        );

        assertEquals(UserErrorCode.EMAIL_VERIFICATION_CODE_INVALID, exception.errorCode());
    }

    @Test
    void signupSellerShouldReturnWrappedResponse() {
        SellerSignupRequest request = new SellerSignupRequest(
                "seller01",
                "Password1!",
                "Password1!",
                "Seller Kim",
                "seller@example.com",
                "SELLER-001",
                "01012345678"
        );

        ApiResponse<Void> response = sellerUserController.signupSeller(request);

        verify(sellerUserCommandService).signUpSeller(request);
        assertTrue(response.success());
        assertEquals(UserSuccessCode.SELLER_SIGNUP_COMPLETED.code(), response.code());
        assertNull(response.data());
    }
}

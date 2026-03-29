package com.wearhouse.user.buyer.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.support.email.EmailVerificationService;
import com.wearhouse.user.domain.dto.request.BuyerSignupRequest;
import com.wearhouse.user.domain.dto.request.EmailCodeSendRequest;
import com.wearhouse.user.domain.dto.request.EmailCodeVerifyRequest;
import com.wearhouse.user.domain.dto.response.LoginIdAvailabilityResponse;
import com.wearhouse.user.domain.exception.UserErrorCode;
import com.wearhouse.user.domain.response.UserSuccessCode;
import com.wearhouse.user.buyer.service.BuyerUserCommandService;
import com.wearhouse.user.buyer.service.BuyerUserQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BuyerUserControllerTest {

    @Mock
    private BuyerUserCommandService buyerUserCommandService;

    @Mock
    private BuyerUserQueryService buyerUserQueryService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private BuyerUserController buyerUserController;

    @Test
    void checkLoginIdAvailabilityShouldReturnWrappedResponse() {
        when(buyerUserQueryService.isLoginIdAvailable("buyer01")).thenReturn(true);

        ApiResponse<LoginIdAvailabilityResponse> response = buyerUserController.checkLoginIdAvailability("buyer01");

        assertTrue(response.success());
        assertEquals(UserSuccessCode.BUYER_LOGIN_ID_AVAILABILITY_CHECKED.code(), response.code());
        assertEquals(true, response.data().available());
    }

    @Test
    void sendEmailCodeShouldReturnWrappedResponse() {
        ApiResponse<Void> response = buyerUserController.sendEmailCode(new EmailCodeSendRequest("buyer@example.com"));

        verify(emailVerificationService).sendCode("buyer@example.com");
        assertTrue(response.success());
        assertEquals(UserSuccessCode.BUYER_EMAIL_CODE_SENT.code(), response.code());
        assertNull(response.data());
    }

    @Test
    void verifyEmailCodeShouldThrowWhenCodeIsInvalid() {
        when(emailVerificationService.verifyCodeAndMarkVerified("buyer@example.com", "123456")).thenReturn(false);

        ErrorException exception = assertThrows(
                ErrorException.class,
                () -> buyerUserController.verifyEmailCode(new EmailCodeVerifyRequest("buyer@example.com", "123456"))
        );

        assertEquals(UserErrorCode.EMAIL_VERIFICATION_CODE_INVALID, exception.errorCode());
    }

    @Test
    void signupBuyerShouldReturnWrappedResponse() {
        BuyerSignupRequest request = new BuyerSignupRequest(
                "buyer01",
                "Password1!",
                "Password1!",
                "Buyer Kim",
                "buyer@example.com",
                "01012345678"
        );

        ApiResponse<Void> response = buyerUserController.signupBuyer(request);

        verify(buyerUserCommandService).signUpBuyer(request);
        assertTrue(response.success());
        assertEquals(UserSuccessCode.BUYER_SIGNUP_COMPLETED.code(), response.code());
        assertNull(response.data());
    }
}

package com.wearhouse.user.domain.service.buyer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.support.email.EmailVerificationService;
import com.wearhouse.user.domain.dto.request.BuyerSignupRequest;
import com.wearhouse.user.domain.entity.BuyerEntity;
import com.wearhouse.user.domain.exception.UserErrorCode;
import com.wearhouse.user.infra.jpa.repository.BuyerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class BuyerCommandServiceTest {

    @Mock
    private BuyerRepository buyerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private BuyerCommandService buyerCommandService;

    @Test
    void signUpBuyerShouldThrowWhenPasswordConfirmMismatch() {
        BuyerSignupRequest request = new BuyerSignupRequest(
                "buyer01",
                "Password1!",
                "Password2!",
                "Buyer Kim",
                "buyer@example.com",
                "01012345678"
        );

        ErrorException exception = assertThrows(ErrorException.class, () -> buyerCommandService.signUpBuyer(request));

        assertEquals(UserErrorCode.SIGNUP_PASSWORD_CONFIRM_MISMATCH, exception.errorCode());
        verify(buyerRepository, never()).save(any());
    }

    @Test
    void signUpBuyerShouldThrowWhenEmailNotVerified() {
        BuyerSignupRequest request = new BuyerSignupRequest(
                "buyer01",
                "Password1!",
                "Password1!",
                "Buyer Kim",
                "buyer@example.com",
                "01012345678"
        );
        when(emailVerificationService.isVerified("buyer@example.com")).thenReturn(false);

        ErrorException exception = assertThrows(ErrorException.class, () -> buyerCommandService.signUpBuyer(request));

        assertEquals(UserErrorCode.EMAIL_VERIFICATION_REQUIRED, exception.errorCode());
        verify(buyerRepository, never()).save(any());
    }

    @Test
    void signUpBuyerShouldThrowWhenDuplicateLoginIdOrEmailExists() {
        BuyerSignupRequest request = new BuyerSignupRequest(
                "buyer01",
                "Password1!",
                "Password1!",
                "Buyer Kim",
                "buyer@example.com",
                "01012345678"
        );
        when(emailVerificationService.isVerified("buyer@example.com")).thenReturn(true);
        when(buyerRepository.existsByLoginIdOrEmail("buyer01", "buyer@example.com")).thenReturn(true);

        ErrorException exception = assertThrows(ErrorException.class, () -> buyerCommandService.signUpBuyer(request));

        assertEquals(UserErrorCode.LOGIN_ID_OR_EMAIL_ALREADY_EXISTS, exception.errorCode());
        verify(buyerRepository, never()).save(any());
    }

    @Test
    void signUpBuyerShouldSaveBuyerWhenRequestIsValid() {
        BuyerSignupRequest request = new BuyerSignupRequest(
                "buyer01",
                "Password1!",
                "Password1!",
                "Buyer Kim",
                "buyer@example.com",
                "01012345678"
        );
        when(emailVerificationService.isVerified("buyer@example.com")).thenReturn(true);
        when(buyerRepository.existsByLoginIdOrEmail("buyer01", "buyer@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1!")).thenReturn("encoded-password");
        when(buyerRepository.save(any(BuyerEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        buyerCommandService.signUpBuyer(request);

        ArgumentCaptor<BuyerEntity> captor = ArgumentCaptor.forClass(BuyerEntity.class);
        verify(buyerRepository).save(captor.capture());
        BuyerEntity saved = captor.getValue();
        assertEquals("buyer01", saved.getLoginId());
        assertEquals("encoded-password", saved.getPassword());
        assertEquals("buyer@example.com", saved.getEmail());
        assertEquals("Buyer Kim", saved.getName());
        verify(emailVerificationService).clearVerified("buyer@example.com");
    }
}

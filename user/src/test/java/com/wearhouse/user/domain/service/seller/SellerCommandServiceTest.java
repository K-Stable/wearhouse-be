package com.wearhouse.user.domain.service.seller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.support.email.EmailVerificationService;
import com.wearhouse.user.domain.dto.request.SellerSignupRequest;
import com.wearhouse.user.domain.entity.SellerEntity;
import com.wearhouse.user.domain.exception.UserErrorCode;
import com.wearhouse.user.infra.jpa.repository.SellerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class SellerCommandServiceTest {

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private SellerCommandService sellerCommandService;

    @Test
    void signUpSellerShouldThrowWhenPasswordConfirmMismatch() {
        SellerSignupRequest request = new SellerSignupRequest(
                "seller01",
                "Password1!",
                "Password2!",
                "Seller Kim",
                "seller@example.com",
                "SELLER-001",
                "01012345678"
        );

        ErrorException exception = assertThrows(ErrorException.class, () -> sellerCommandService.signUpSeller(request));

        assertEquals(UserErrorCode.SIGNUP_PASSWORD_CONFIRM_MISMATCH, exception.errorCode());
        verify(sellerRepository, never()).save(any());
    }

    @Test
    void signUpSellerShouldThrowWhenEmailNotVerified() {
        SellerSignupRequest request = new SellerSignupRequest(
                "seller01",
                "Password1!",
                "Password1!",
                "Seller Kim",
                "seller@example.com",
                "SELLER-001",
                "01012345678"
        );
        when(emailVerificationService.isVerified("seller@example.com")).thenReturn(false);

        ErrorException exception = assertThrows(ErrorException.class, () -> sellerCommandService.signUpSeller(request));

        assertEquals(UserErrorCode.EMAIL_VERIFICATION_REQUIRED, exception.errorCode());
        verify(sellerRepository, never()).save(any());
    }

    @Test
    void signUpSellerShouldThrowWhenDuplicateLoginIdOrEmailExists() {
        SellerSignupRequest request = new SellerSignupRequest(
                "seller01",
                "Password1!",
                "Password1!",
                "Seller Kim",
                "seller@example.com",
                "SELLER-001",
                "01012345678"
        );
        when(emailVerificationService.isVerified("seller@example.com")).thenReturn(true);
        when(sellerRepository.existsByLoginIdOrEmail("seller01", "seller@example.com")).thenReturn(true);

        ErrorException exception = assertThrows(ErrorException.class, () -> sellerCommandService.signUpSeller(request));

        assertEquals(UserErrorCode.LOGIN_ID_OR_EMAIL_ALREADY_EXISTS, exception.errorCode());
        verify(sellerRepository, never()).save(any());
    }

    @Test
    void signUpSellerShouldSaveSellerWhenRequestIsValid() {
        SellerSignupRequest request = new SellerSignupRequest(
                "seller01",
                "Password1!",
                "Password1!",
                "Seller Kim",
                "seller@example.com",
                "SELLER-001",
                "01012345678"
        );
        when(emailVerificationService.isVerified("seller@example.com")).thenReturn(true);
        when(sellerRepository.existsByLoginIdOrEmail("seller01", "seller@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1!")).thenReturn("encoded-password");
        when(sellerRepository.save(any(SellerEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        sellerCommandService.signUpSeller(request);

        ArgumentCaptor<SellerEntity> captor = ArgumentCaptor.forClass(SellerEntity.class);
        verify(sellerRepository).save(captor.capture());
        SellerEntity saved = captor.getValue();
        assertEquals("seller01", saved.getLoginId());
        assertEquals("encoded-password", saved.getPassword());
        assertEquals("seller@example.com", saved.getEmail());
        assertEquals("Seller Kim", saved.getName());
        assertEquals("SELLER-001", saved.getSellerNo());
        verify(emailVerificationService).clearVerified("seller@example.com");
    }
}

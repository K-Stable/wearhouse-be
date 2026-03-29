package com.wearhouse.user.seller.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.common.support.email.EmailVerificationService;
import com.wearhouse.user.domain.dto.request.SellerSignupRequest;
import com.wearhouse.user.domain.entity.SellerEntity;
import com.wearhouse.user.domain.exception.UserErrorCode;
import com.wearhouse.user.infra.jpa.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SellerUserCommandService {

    private final SellerRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    @WriteTx
    public void signUpSeller(SellerSignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new ErrorException(UserErrorCode.SIGNUP_PASSWORD_CONFIRM_MISMATCH);
        }

        if (!emailVerificationService.isVerified(request.email())) {
            throw new ErrorException(UserErrorCode.EMAIL_VERIFICATION_REQUIRED);
        }

        if (sellerRepository.existsByLoginIdOrEmail(request.loginId(), request.email())) {
            throw new ErrorException(UserErrorCode.LOGIN_ID_OR_EMAIL_ALREADY_EXISTS);
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        SellerEntity seller = SellerEntity.create(
                request.loginId(),
                request.email(),
                encodedPassword,
                request.name(),
                request.phone(),
                request.sellerNo()
        );
        sellerRepository.save(seller);
        emailVerificationService.clearVerified(request.email());
    }
}

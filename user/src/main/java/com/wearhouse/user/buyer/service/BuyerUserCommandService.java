package com.wearhouse.user.buyer.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.common.support.email.EmailVerificationService;
import com.wearhouse.user.domain.dto.request.BuyerSignupRequest;
import com.wearhouse.user.domain.entity.BuyerEntity;
import com.wearhouse.user.domain.exception.UserErrorCode;
import com.wearhouse.user.infra.jpa.repository.BuyerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuyerUserCommandService {

    private final BuyerRepository buyerRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    @WriteTx
    public void signUpBuyer(BuyerSignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new ErrorException(UserErrorCode.SIGNUP_PASSWORD_CONFIRM_MISMATCH);
        }

        if (!emailVerificationService.isVerified(request.email())) {
            throw new ErrorException(UserErrorCode.EMAIL_VERIFICATION_REQUIRED);
        }

        if (buyerRepository.existsByLoginIdOrEmail(request.loginId(), request.email())) {
            throw new ErrorException(UserErrorCode.LOGIN_ID_OR_EMAIL_ALREADY_EXISTS);
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        BuyerEntity buyer = BuyerEntity.create(
                request.loginId(),
                request.email(),
                encodedPassword,
                request.name(),
                request.phone()
        );
        buyerRepository.save(buyer);
        emailVerificationService.clearVerified(request.email());
    }
}

package com.wearhouse.user.domain.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.user.domain.dto.request.UserSignupRequest;
import com.wearhouse.user.domain.dto.response.UserSignupResponse;
import com.wearhouse.user.domain.entity.BuyerEntity;
import com.wearhouse.user.domain.entity.SellerEntity;
import com.wearhouse.user.domain.exception.UserErrorCode;
import com.wearhouse.user.domain.model.UserAuthAccount;
import com.wearhouse.user.domain.model.UserType;
import com.wearhouse.user.infra.auth.AuthSignupClient;
import com.wearhouse.user.infra.auth.dto.AuthInternalSignupResponse;
import com.wearhouse.user.infra.jpa.repository.BuyerRepository;
import com.wearhouse.user.infra.jpa.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuyerService {

    private final AuthSignupClient authSignupClient;
    private final BuyerRepository buyerRepository;
    private final SellerRepository sellerRepository;

    public UserSignupSession signupBuyer(UserSignupRequest request) {
        AuthInternalSignupResponse response = authSignupClient.signupBuyer(
                request.email(),
                request.password(),
                request.displayName()
        );
        return toSession(response);
    }

    public UserSignupSession signupSeller(UserSignupRequest request) {
        AuthInternalSignupResponse response = authSignupClient.signupSeller(
                request.email(),
                request.password(),
                request.displayName()
        );
        return toSession(response);
    }

    @WriteTx
    public UserAuthAccount signup(UserType userType, String email, String passwordHash, String displayName) {
        return switch (userType) {
            case BUYER -> signupBuyerInternal(email, passwordHash, displayName);
            case SELLER -> signupSellerInternal(email, passwordHash, displayName);
        };
    }

    @ReadTx
    public UserAuthAccount findByEmail(UserType userType, String email) {
        return switch (userType) {
            case BUYER -> buyerRepository.findByEmail(email)
                    .map(this::toBuyerAuthAccount)
                    .orElseThrow(() -> new ErrorException(UserErrorCode.USER_NOT_FOUND));
            case SELLER -> sellerRepository.findByEmail(email)
                    .map(this::toSellerAuthAccount)
                    .orElseThrow(() -> new ErrorException(UserErrorCode.USER_NOT_FOUND));
        };
    }

    @ReadTx
    public UserAuthAccount findById(UserType userType, Long userId) {
        return switch (userType) {
            case BUYER -> buyerRepository.findById(userId)
                    .map(this::toBuyerAuthAccount)
                    .orElseThrow(() -> new ErrorException(UserErrorCode.USER_NOT_FOUND));
            case SELLER -> sellerRepository.findById(userId)
                    .map(this::toSellerAuthAccount)
                    .orElseThrow(() -> new ErrorException(UserErrorCode.USER_NOT_FOUND));
        };
    }

    private UserAuthAccount signupBuyerInternal(String email, String passwordHash, String displayName) {
        if (buyerRepository.existsByEmail(email)) {
            throw new ErrorException(UserErrorCode.EMAIL_ALREADY_EXISTS);
        }
        BuyerEntity buyer = buyerRepository.save(BuyerEntity.create(email, passwordHash, displayName));
        return toBuyerAuthAccount(buyer);
    }

    private UserAuthAccount signupSellerInternal(String email, String passwordHash, String displayName) {
        if (sellerRepository.existsByEmail(email)) {
            throw new ErrorException(UserErrorCode.EMAIL_ALREADY_EXISTS);
        }
        SellerEntity seller = sellerRepository.save(SellerEntity.create(email, passwordHash, displayName));
        return toSellerAuthAccount(seller);
    }

    private UserAuthAccount toBuyerAuthAccount(BuyerEntity entity) {
        return new UserAuthAccount(
                entity.getId(),
                UserType.BUYER,
                entity.getEmail(),
                entity.getPassword(),
                entity.getStatus(),
                entity.getUserVersion()
        );
    }

    private UserAuthAccount toSellerAuthAccount(SellerEntity entity) {
        return new UserAuthAccount(
                entity.getId(),
                UserType.SELLER,
                entity.getEmail(),
                entity.getPassword(),
                entity.getStatus(),
                entity.getUserVersion()
        );
    }

    private UserSignupSession toSession(AuthInternalSignupResponse response) {
        UserSignupResponse payload = new UserSignupResponse(
                response.userId(),
                response.userType(),
                response.email(),
                response.accessTokenExpiresAt()
        );
        return new UserSignupSession(payload, response.accessToken(), response.refreshToken());
    }

    public record UserSignupSession(
            UserSignupResponse response,
            String accessToken,
            String refreshToken
    ) {
    }
}

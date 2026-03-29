package com.wearhouse.user.internal.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.user.domain.entity.BuyerEntity;
import com.wearhouse.user.domain.entity.SellerEntity;
import com.wearhouse.user.domain.exception.UserErrorCode;
import com.wearhouse.user.domain.model.UserAuthAccount;
import com.wearhouse.user.domain.model.UserType;
import com.wearhouse.user.infra.jpa.repository.BuyerRepository;
import com.wearhouse.user.infra.jpa.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserInternalAuthQueryService {

    private final BuyerRepository buyerRepository;
    private final SellerRepository sellerRepository;

    @ReadTx
    public UserAuthAccount findByLoginId(UserType userType, String loginId) {
        return switch (userType) {
            case BUYER -> buyerRepository.findByLoginId(loginId)
                    .map(this::toBuyerAuthAccount)
                    .orElseThrow(() -> new ErrorException(UserErrorCode.USER_NOT_FOUND));
            case SELLER -> sellerRepository.findByLoginId(loginId)
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
}

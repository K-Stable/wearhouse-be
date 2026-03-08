package com.wearhouse.user.domain.service.command;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.user.domain.entity.BuyerEntity;
import com.wearhouse.user.domain.entity.SellerEntity;
import com.wearhouse.user.domain.exception.UserErrorCode;
import com.wearhouse.user.domain.model.UserAuthAccount;
import com.wearhouse.user.domain.model.UserType;
import com.wearhouse.user.infra.jpa.repository.BuyerRepository;
import com.wearhouse.user.infra.jpa.repository.SellerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserInternalAuthCommandService {

    private final BuyerRepository buyerRepository;
    private final SellerRepository sellerRepository;

    public UserInternalAuthCommandService(
            BuyerRepository buyerRepository,
            SellerRepository sellerRepository
    ) {
        this.buyerRepository = buyerRepository;
        this.sellerRepository = sellerRepository;
    }

    @Transactional
    public UserAuthAccount signup(UserType userType, String email, String passwordHash, String displayName) {
        return switch (userType) {
            case BUYER -> signupBuyer(email, passwordHash, displayName);
            case SELLER -> signupSeller(email, passwordHash, displayName);
        };
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
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

    private UserAuthAccount signupBuyer(String email, String passwordHash, String displayName) {
        if (buyerRepository.existsByEmail(email)) {
            throw new ErrorException(UserErrorCode.EMAIL_ALREADY_EXISTS);
        }
        BuyerEntity buyer = buyerRepository.save(BuyerEntity.create(email, passwordHash, displayName));
        return toBuyerAuthAccount(buyer);
    }

    private UserAuthAccount signupSeller(String email, String passwordHash, String displayName) {
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
}

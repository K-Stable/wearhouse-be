package com.wearhouse.user.domain.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.security.current.CurrentUserPrincipal;
import com.wearhouse.user.domain.dto.request.AddressCreateRequest;
import com.wearhouse.user.domain.dto.request.AddressUpdateRequest;
import com.wearhouse.user.domain.dto.request.PasswordChangeRequest;
import com.wearhouse.user.domain.dto.response.UserAddressResponse;
import com.wearhouse.user.domain.dto.response.UserProfileResponse;
import com.wearhouse.user.domain.entity.BuyerEntity;
import com.wearhouse.user.domain.entity.SellerEntity;
import com.wearhouse.user.domain.entity.UserAddressEntity;
import com.wearhouse.user.domain.exception.UserErrorCode;
import com.wearhouse.user.domain.model.UserType;
import com.wearhouse.user.infra.jpa.repository.BuyerRepository;
import com.wearhouse.user.infra.jpa.repository.SellerRepository;
import com.wearhouse.user.infra.jpa.repository.UserAddressRepository;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

    private final BuyerRepository buyerRepository;
    private final SellerRepository sellerRepository;
    private final UserAddressRepository userAddressRepository;
    private final PasswordEncoder passwordEncoder;

    public UserProfileService(
            BuyerRepository buyerRepository,
            SellerRepository sellerRepository,
            UserAddressRepository userAddressRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.buyerRepository = buyerRepository;
        this.sellerRepository = sellerRepository;
        this.userAddressRepository = userAddressRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(CurrentUserPrincipal currentUser) {
        UserType userType = parseUserType(currentUser.userType());
        return switch (userType) {
            case BUYER -> buyerRepository.findById(currentUser.userId())
                    .map(entity -> new UserProfileResponse(
                            entity.getId(),
                            "BUYER",
                            entity.getEmail(),
                            entity.getName(),
                            entity.getStatus(),
                            entity.getUserVersion()
                    ))
                    .orElseThrow(() -> new ErrorException(UserErrorCode.USER_NOT_FOUND));
            case SELLER -> sellerRepository.findById(currentUser.userId())
                    .map(entity -> new UserProfileResponse(
                            entity.getId(),
                            "SELLER",
                            entity.getEmail(),
                            entity.getName(),
                            entity.getStatus(),
                            entity.getUserVersion()
                    ))
                    .orElseThrow(() -> new ErrorException(UserErrorCode.USER_NOT_FOUND));
        };
    }

    @Transactional
    public void changePassword(CurrentUserPrincipal currentUser, PasswordChangeRequest request) {
        UserType userType = parseUserType(currentUser.userType());
        switch (userType) {
            case BUYER -> changeBuyerPassword(currentUser.userId(), request);
            case SELLER -> changeSellerPassword(currentUser.userId(), request);
        }
    }

    @Transactional(readOnly = true)
    public List<UserAddressResponse> getMyAddresses(CurrentUserPrincipal currentUser) {
        List<UserAddressEntity> addresses = userAddressRepository.findByUserTypeAndUserIdOrderByIsDefaultDescIdDesc(
                currentUser.userType(),
                currentUser.userId()
        );
        return addresses.stream().map(this::toAddressResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserAddressResponse getMyDefaultAddress(CurrentUserPrincipal currentUser) {
        return userAddressRepository.findFirstByUserTypeAndUserIdAndIsDefaultTrue(currentUser.userType(), currentUser.userId())
                .map(this::toAddressResponse)
                .orElseThrow(() -> new ErrorException(UserErrorCode.ADDRESS_NOT_FOUND));
    }

    @Transactional
    public UserAddressResponse createAddress(CurrentUserPrincipal currentUser, AddressCreateRequest request) {
        boolean shouldDefault = Boolean.TRUE.equals(request.defaultAddress())
                || !userAddressRepository.existsByUserTypeAndUserIdAndIsDefaultTrue(currentUser.userType(), currentUser.userId());
        if (shouldDefault) {
            userAddressRepository.clearDefault(currentUser.userType(), currentUser.userId());
        }
        UserAddressEntity address = userAddressRepository.save(UserAddressEntity.create(
                currentUser.userType(),
                currentUser.userId(),
                request.label(),
                request.recipientName(),
                request.recipientPhone(),
                request.zipCode(),
                request.address1(),
                request.address2(),
                shouldDefault
        ));
        return toAddressResponse(address);
    }

    @Transactional
    public UserAddressResponse updateAddress(CurrentUserPrincipal currentUser, Long addressId, AddressUpdateRequest request) {
        UserAddressEntity address = userAddressRepository.findByIdAndUserTypeAndUserId(
                        addressId, currentUser.userType(), currentUser.userId()
                )
                .orElseThrow(() -> new ErrorException(UserErrorCode.ADDRESS_NOT_FOUND));
        address.update(
                request.label(),
                request.recipientName(),
                request.recipientPhone(),
                request.zipCode(),
                request.address1(),
                request.address2()
        );
        return toAddressResponse(address);
    }

    @Transactional
    public UserAddressResponse setDefaultAddress(CurrentUserPrincipal currentUser, Long addressId) {
        UserAddressEntity address = userAddressRepository.findByIdAndUserTypeAndUserId(
                        addressId, currentUser.userType(), currentUser.userId()
                )
                .orElseThrow(() -> new ErrorException(UserErrorCode.ADDRESS_NOT_FOUND));
        userAddressRepository.clearDefault(currentUser.userType(), currentUser.userId());
        address.markDefault(true);
        return toAddressResponse(address);
    }

    @Transactional
    public void deleteAddress(CurrentUserPrincipal currentUser, Long addressId) {
        UserAddressEntity address = userAddressRepository.findByIdAndUserTypeAndUserId(
                        addressId, currentUser.userType(), currentUser.userId()
                )
                .orElseThrow(() -> new ErrorException(UserErrorCode.ADDRESS_NOT_FOUND));
        boolean wasDefault = address.isDefault();
        userAddressRepository.delete(address);

        if (wasDefault) {
            userAddressRepository.findByUserTypeAndUserIdOrderByIsDefaultDescIdDesc(currentUser.userType(), currentUser.userId())
                    .stream()
                    .findFirst()
                    .ifPresent(first -> first.markDefault(true));
        }
    }

    private void changeBuyerPassword(Long userId, PasswordChangeRequest request) {
        BuyerEntity buyer = buyerRepository.findById(userId)
                .orElseThrow(() -> new ErrorException(UserErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(request.currentPassword(), buyer.getPassword())) {
            throw new ErrorException(UserErrorCode.PASSWORD_MISMATCH);
        }
        buyer.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    private void changeSellerPassword(Long userId, PasswordChangeRequest request) {
        SellerEntity seller = sellerRepository.findById(userId)
                .orElseThrow(() -> new ErrorException(UserErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(request.currentPassword(), seller.getPassword())) {
            throw new ErrorException(UserErrorCode.PASSWORD_MISMATCH);
        }
        seller.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    private UserAddressResponse toAddressResponse(UserAddressEntity entity) {
        return new UserAddressResponse(
                entity.getId(),
                entity.getLabel(),
                entity.getRecipientName(),
                entity.getRecipientPhone(),
                entity.getZipCode(),
                entity.getAddress1(),
                entity.getAddress2(),
                entity.isDefault()
        );
    }

    private UserType parseUserType(String raw) {
        try {
            return UserType.valueOf(raw);
        } catch (Exception exception) {
            throw new ErrorException(UserErrorCode.USER_TYPE_INVALID);
        }
    }
}

package com.wearhouse.user.domain.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.user.domain.dto.response.InternalBuyerOrderPreviewResponse;
import com.wearhouse.user.domain.dto.response.InternalBuyerOrderPreviewResponse.BuyerDefaultAddressResponse;
import com.wearhouse.user.domain.entity.BuyerAddressEntity;
import com.wearhouse.user.domain.entity.BuyerEntity;
import com.wearhouse.user.domain.exception.UserErrorCode;
import com.wearhouse.user.infra.jpa.repository.BuyerRepository;
import com.wearhouse.user.infra.jpa.repository.UserAddressRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserInternalOrderPreviewQueryService {

    private static final String BUYER_TYPE = "BUYER";

    private final BuyerRepository buyerRepository;
    private final UserAddressRepository userAddressRepository;

    @ReadTx
    public InternalBuyerOrderPreviewResponse getBuyerPreviewInfo(Long buyerId) {
        BuyerEntity buyer = buyerRepository.findById(buyerId)
                .orElseThrow(() -> new ErrorException(UserErrorCode.USER_NOT_FOUND));
        BuyerDefaultAddressResponse addressResponse = userAddressRepository
                .findFirstByUserTypeAndUserIdAndIsDefaultTrue(BUYER_TYPE, buyerId)
                .map(this::toAddressResponse)
                .orElse(null);

        return new InternalBuyerOrderPreviewResponse(
                buyer.getId(),
                parsePoint(buyer.getPoint()),
                addressResponse
        );
    }

    private BuyerDefaultAddressResponse toAddressResponse(BuyerAddressEntity entity) {
        return new BuyerDefaultAddressResponse(
                entity.getId(),
                entity.getLabel(),
                entity.getRecipientName(),
                entity.getRecipientPhone(),
                entity.getZipCode(),
                entity.getAddress1(),
                entity.getAddress2()
        );
    }

    private BigDecimal parsePoint(String rawPoint) {
        if (rawPoint == null || rawPoint.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(rawPoint.trim());
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }
}

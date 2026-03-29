package com.wearhouse.user.seller.service;

import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.user.infra.jpa.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SellerUserQueryService {

    private final SellerRepository sellerRepository;

    @ReadTx
    public boolean isLoginIdAvailable(String loginId) {
        return !sellerRepository.existsByLoginId(loginId);
    }
}
